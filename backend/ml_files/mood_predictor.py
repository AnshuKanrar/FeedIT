# empty for now, real file added next step
"""
mood_predictor.py
------------------
Predicts a user's mood after a feed session using the valence-arousal
"circumplex model of affect" (Russell, 1980) instead of hardcoded
if/else rules.

INPUT (per post the user viewed during the session):
    ML scores already produced by your toxicity/emotion models:
        toxic, obscene, threat, insult, joy, sadness   (all 0..1)
    Lightweight behavioural signals the Android app can log for free:
        dwell_time_sec   -> how long they looked at the post
        scroll_speed     -> px/sec or posts/sec, however the app measures it

OUTPUT (what you hand the FastAPI person, /mood-check endpoint):
    {
      "mood": "happy" | "calm" | "sad" | "neutral",
      "valence": float [-1, 1],
      "arousal": float [-1, 1],
      "confidence": float [0, 1],
      "trend": "improving" | "declining" | "stable",
      "session_minutes": float,
      "history": [ {t, valence, arousal}, ... ]   # for a mood graph on the dashboard
    }

THE MATH (all standard, citable techniques -- nothing hand-waved, good for
your methodology slide):
    1. Exponential Moving Average (EMA) smooths noisy per-post scores into
       a session-level signal.
    2. Welford's online algorithm keeps a running mean/variance of the
       user's OWN valence history across past sessions, so "happy" is
       relative to that person's normal baseline, not one global cutoff.
    3. Russell's Circumplex Model of Affect (1980, a well-established
       psychology model) maps emotion onto a 2-D valence x arousal plane.
       The mood label is whichever quadrant the (valence, arousal) point
       falls in -- computed with polar coordinates (r, theta), not nested
       if/else thresholds.
    4. Ordinary least-squares regression slope on the valence time series
       reports whether the session is trending up or down.
"""

import math
import time
from dataclasses import dataclass, field
from typing import List, Optional


# ---------------------------------------------------------------------
# 1. Per-post content -> (valence, arousal)
# ---------------------------------------------------------------------

# The ONLY tunable knobs in this file. Everything else below is derived
# mathematically from these. Re-tune once you run this against real
# posts.json scores.
VALENCE_WEIGHTS = {
    "joy": 1.0,
    "sadness": -1.0,
    "toxic": -0.6,
    "obscene": -0.4,
    "insult": -0.6,
    "threat": -0.8,
}

# "Activating" content pushes arousal up; "deactivating" content pulls it down.
AROUSAL_CONTENT_WEIGHTS = {
    "joy": 0.5,
    "threat": 0.9,
    "toxic": 0.6,
    "obscene": 0.3,
    "insult": 0.4,
    "sadness": -0.5,   # sadness is a low-arousal ("deactivated") negative emotion
}


def _tanh_normalize(x: float, scale: float = 1.0) -> float:
    """Squash an unbounded weighted sum smoothly into (-1, 1)."""
    return math.tanh(x / scale)


def content_valence(scores: dict) -> float:
    raw = sum(VALENCE_WEIGHTS.get(k, 0.0) * scores.get(k, 0.0) for k in VALENCE_WEIGHTS)
    return _tanh_normalize(raw, scale=1.5)


def content_arousal(scores: dict) -> float:
    raw = sum(AROUSAL_CONTENT_WEIGHTS.get(k, 0.0) * scores.get(k, 0.0) for k in AROUSAL_CONTENT_WEIGHTS)
    return _tanh_normalize(raw, scale=1.5)


def behavioral_arousal(dwell_time_sec: float, scroll_speed: float,
                        avg_dwell: float, avg_scroll: float) -> float:
    """
    Fast scrolling / short dwell => agitated, high-arousal "doomscrolling".
    Slow scrolling / long dwell  => settled, low-arousal browsing.
    Both are z-scored against the session's OWN running average so it
    adapts to each user's normal scrolling style instead of a fixed number.
    """
    dwell_z = 0.0 if avg_dwell == 0 else (avg_dwell - dwell_time_sec) / max(avg_dwell, 1e-6)
    scroll_z = 0.0 if avg_scroll == 0 else (scroll_speed - avg_scroll) / max(avg_scroll, 1e-6)
    raw = 0.5 * dwell_z + 0.5 * scroll_z
    return _tanh_normalize(raw, scale=2.0)


# ---------------------------------------------------------------------
# 2. Welford's online mean/variance -- per-user baseline across sessions
# ---------------------------------------------------------------------

@dataclass
class RunningStats:
    """Running mean & variance across ALL of a user's sessions (not just
    this one). Persist this object (e.g. serialize to posts.json / a
    per-user row) between app launches so the baseline actually accumulates."""
    n: int = 0
    mean: float = 0.0
    m2: float = 0.0

    def update(self, x: float):
        self.n += 1
        delta = x - self.mean
        self.mean += delta / self.n
        delta2 = x - self.mean
        self.m2 += delta * delta2

    @property
    def std(self) -> float:
        if self.n < 2:
            return 1.0  # avoid div-by-zero for a brand-new user
        return math.sqrt(self.m2 / (self.n - 1))

    def zscore(self, x: float) -> float:
        return (x - self.mean) / self.std if self.std > 1e-9 else 0.0


# ---------------------------------------------------------------------
# 3. Session tracking + final classification
# ---------------------------------------------------------------------

@dataclass
class MoodEvent:
    t: float          # seconds since session start
    valence: float
    arousal: float


@dataclass
class MoodSession:
    alpha: float = 0.25            # EMA smoothing factor (higher = reacts faster to new posts)
    neutral_radius: float = 0.18   # |vector| below this => "neutral" -- tune on real data
    events: List[MoodEvent] = field(default_factory=list)
    ema_valence: Optional[float] = None
    ema_arousal: Optional[float] = None
    _dwell_sum: float = 0.0
    _scroll_sum: float = 0.0
    _count: int = 0

    def add_post(self, scores: dict, dwell_time_sec: float, scroll_speed: float,
                 t: Optional[float] = None):
        """Call this once per post the user views. `scores` is the dict your
        toxicity+emotion model already outputs: toxic/obscene/threat/insult/joy/sadness."""
        self._count += 1
        avg_dwell = self._dwell_sum / max(self._count - 1, 1)
        avg_scroll = self._scroll_sum / max(self._count - 1, 1)

        v = content_valence(scores)
        a = 0.7 * content_arousal(scores) + 0.3 * behavioral_arousal(
            dwell_time_sec, scroll_speed, avg_dwell, avg_scroll
        )

        self._dwell_sum += dwell_time_sec
        self._scroll_sum += scroll_speed

        self.ema_valence = v if self.ema_valence is None else (
            self.alpha * v + (1 - self.alpha) * self.ema_valence
        )
        self.ema_arousal = a if self.ema_arousal is None else (
            self.alpha * a + (1 - self.alpha) * self.ema_arousal
        )

        self.events.append(MoodEvent(
            t=t if t is not None else time.time(),
            valence=self.ema_valence,
            arousal=self.ema_arousal,
        ))

    def _valence_trend(self) -> str:
        """OLS regression slope of valence over the session -- reuse the same
        idea as the trend slope already in feed_logic.py."""
        n = len(self.events)
        if n < 3:
            return "stable"
        xs = [e.t - self.events[0].t for e in self.events]
        ys = [e.valence for e in self.events]
        x_mean = sum(xs) / n
        y_mean = sum(ys) / n
        num = sum((x - x_mean) * (y - y_mean) for x, y in zip(xs, ys))
        den = sum((x - x_mean) ** 2 for x in xs) or 1e-9
        slope = num / den
        if slope > 0.01:
            return "improving"
        elif slope < -0.01:
            return "declining"
        return "stable"

    def predict(self, baseline: Optional[RunningStats] = None) -> dict:
        if not self.events:
            return {"mood": "neutral", "valence": 0.0, "arousal": 0.0,
                     "confidence": 0.0, "trend": "stable",
                     "session_minutes": 0.0, "history": []}

        v, a = self.ema_valence, self.ema_arousal

        # Personalize against this user's own history, once we have enough of it
        if baseline is not None and baseline.n >= 5:
            v = _tanh_normalize(baseline.zscore(v), scale=2.0)

        r = math.hypot(v, a)          # magnitude -> how strong the mood signal is
        # theta (quadrant) drives the label; r drives confidence + neutral cutoff
        if r < self.neutral_radius:
            mood = "neutral"
        elif v >= 0 and a >= 0:
            mood = "happy"            # positive valence, high arousal
        elif v >= 0 and a < 0:
            mood = "calm"             # positive valence, low arousal
        else:
            mood = "sad"              # negative valence (either arousal level
                                        # collapses to "sad" -- see note below)

        session_minutes = (self.events[-1].t - self.events[0].t) / 60.0

        return {
            "mood": mood,
            "valence": round(v, 3),
            "arousal": round(a, 3),
            "confidence": round(min(r, 1.0), 3),
            "trend": self._valence_trend(),
            "session_minutes": round(session_minutes, 1),
            "history": [
                {"t": round(e.t - self.events[0].t, 1),
                 "valence": round(e.valence, 3),
                 "arousal": round(e.arousal, 3)}
                for e in self.events
            ],
        }


# ---------------------------------------------------------------------
# FastAPI integration sketch (give this shape to the ML/backend person)
# ---------------------------------------------------------------------
"""
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()
session_store = {}     # user_id -> MoodSession   (in-memory is fine for a demo)
baseline_store = {}    # user_id -> RunningStats  (persist this across sessions if you can)

class PostEvent(BaseModel):
    user_id: str
    scores: dict          # {"toxic":.., "obscene":.., "threat":.., "insult":.., "joy":.., "sadness":..}
    dwell_time_sec: float
    scroll_speed: float

@app.post("/session/event")
def log_event(ev: PostEvent):
    session = session_store.setdefault(ev.user_id, MoodSession())
    session.add_post(ev.scores, ev.dwell_time_sec, ev.scroll_speed)
    return {"ok": True}

@app.get("/session/mood/{user_id}")
def get_mood(user_id: str):
    session = session_store.get(user_id, MoodSession())
    baseline = baseline_store.setdefault(user_id, RunningStats())
    result = session.predict(baseline)
    baseline.update(result["valence"])   # feed this session's result back into the baseline
    return result
"""


# ---------------------------------------------------------------------
# Quick demo / sanity check -- run: python mood_predictor.py
# ---------------------------------------------------------------------
if __name__ == "__main__":
    import random

    def fake_post(bias: str):
        """bias: 'happy_feed', 'toxic_feed', or 'calm_feed' -- for testing only."""
        if bias == "toxic_feed":
            return dict(toxic=random.uniform(0.5, 0.9), obscene=random.uniform(0.2, 0.6),
                        threat=random.uniform(0.1, 0.5), insult=random.uniform(0.3, 0.7),
                        joy=random.uniform(0, 0.1), sadness=random.uniform(0.1, 0.4))
        if bias == "calm_feed":
            return dict(toxic=0.02, obscene=0.0, threat=0.0, insult=0.02,
                        joy=random.uniform(0.2, 0.4), sadness=random.uniform(0, 0.1))
        return dict(toxic=0.02, obscene=0.0, threat=0.0, insult=0.0,
                    joy=random.uniform(0.6, 0.9), sadness=0.0)

    for feed_type in ["toxic_feed", "calm_feed", "happy_feed"]:
        s = MoodSession()
        t = 0.0
        for i in range(40):
            t += random.uniform(20, 90)  # seconds between posts
            dwell = random.uniform(2, 6) if feed_type != "toxic_feed" else random.uniform(1, 3)
            scroll = random.uniform(50, 150) if feed_type != "toxic_feed" else random.uniform(200, 400)
            s.add_post(fake_post(feed_type), dwell_time_sec=dwell, scroll_speed=scroll, t=t)
        result = s.predict()
        print(f"{feed_type:12s} -> mood={result['mood']:8s} "
              f"valence={result['valence']:+.2f} arousal={result['arousal']:+.2f} "
              f"confidence={result['confidence']:.2f} trend={result['trend']}")
