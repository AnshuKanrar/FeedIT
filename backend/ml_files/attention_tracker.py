"""
attention_tracker.py
---------------------
Tracks how many posts ("reels") a user scrolls past per minute, over a
rolling hour-long session, so the Analysis screen can show how someone's
attention span is trending: are they slowing down and reading more, or
speeding up and skimming/doomscrolling?

INPUT: one call per post the user scrolls past during the session
(log_scroll() - called automatically, once per post the backend already
processes for that user; no separate endpoint or extra round trip needed).

OUTPUT (AttentionTracker.summary(), what /session/attention/{user_id} returns):
    {
      "scrolls_last_minute": int,
      "avg_scrolls_per_minute": float,
      "session_minutes": float,
      "trend": "improving" | "declining" | "stable",
      "per_minute_counts": [ {"minute": 0, "count": 4}, ... ]   # for a bar chart
    }

THE MATH:
    - Every scroll event is stamped with wall-clock time and bucketed into
      1-minute buckets (elapsed seconds // 60), the same bucketing idea as
      a per-minute rate limiter.
    - Buckets are capped to the last 60 (a rolling 1-hour window) so a
      long-running session doesn't grow the list forever.
    - OLS regression slope on the per-minute counts (same technique already
      used for the wellbeing trend in feed_logic.py and the valence trend
      in mood_predictor.py) reports whether the scroll rate is trending up
      or down.
    - Attention "improves" when the scroll rate is SLOWING DOWN (spending
      longer per post = negative slope); it "declines" when the scroll
      rate is SPEEDING UP (skimming faster = positive slope, the classic
      doomscrolling signal).
"""

import time
from dataclasses import dataclass, field
from typing import List, Optional

MAX_MINUTES = 60  # rolling 1-hour window
TREND_FLAT_BAND = 0.5  # slope within +/- this counts as "stable"


def _trend_slope(counts: List[int]) -> float:
    n = len(counts)
    if n < 3:
        return 0.0
    x_mean = (n - 1) / 2
    y_mean = sum(counts) / n
    num = sum((i - x_mean) * (c - y_mean) for i, c in enumerate(counts))
    den = sum((i - x_mean) ** 2 for i in range(n))
    return num / den if den != 0 else 0.0


@dataclass
class AttentionTracker:
    session_start: float = field(default_factory=time.time)
    minute_counts: List[int] = field(default_factory=list)

    def log_scroll(self, t: Optional[float] = None):
        """Call once per post the user scrolls past/views."""
        now = t if t is not None else time.time()
        elapsed_minutes = int((now - self.session_start) // 60)

        while len(self.minute_counts) <= elapsed_minutes:
            self.minute_counts.append(0)

        if len(self.minute_counts) > MAX_MINUTES:
            overflow = len(self.minute_counts) - MAX_MINUTES
            self.minute_counts = self.minute_counts[overflow:]
            elapsed_minutes -= overflow

        index = min(max(elapsed_minutes, 0), len(self.minute_counts) - 1)
        self.minute_counts[index] += 1

    def summary(self) -> dict:
        if not self.minute_counts:
            return {
                "scrolls_last_minute": 0,
                "avg_scrolls_per_minute": 0.0,
                "session_minutes": 0.0,
                "trend": "stable",
                "per_minute_counts": [],
            }

        slope = _trend_slope(self.minute_counts)
        if slope > TREND_FLAT_BAND:
            trend = "declining"
        elif slope < -TREND_FLAT_BAND:
            trend = "improving"
        else:
            trend = "stable"

        return {
            "scrolls_last_minute": self.minute_counts[-1],
            "avg_scrolls_per_minute": round(sum(self.minute_counts) / len(self.minute_counts), 2),
            "session_minutes": round(len(self.minute_counts), 1),
            "trend": trend,
            "per_minute_counts": [
                {"minute": i, "count": c} for i, c in enumerate(self.minute_counts)
            ],
        }


# ---------------------------------------------------------------------
# Quick demo / sanity check -- run: python attention_tracker.py
# ---------------------------------------------------------------------
if __name__ == "__main__":
    # Simulate a session that starts calm (1 scroll/min) then speeds up
    # into doomscrolling (5+ scrolls/min).
    tracker = AttentionTracker(session_start=0.0)
    t = 0.0
    for minute in range(10):
        scrolls_this_minute = 1 + minute  # accelerating scroll rate
        for _ in range(scrolls_this_minute):
            tracker.log_scroll(t=t)
            t += 60.0 / scrolls_this_minute
    import json
    print(json.dumps(tracker.summary(), indent=2))
