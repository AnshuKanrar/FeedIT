"""
FastAPI server exposing POST /classify and GET /session/mood/{user_id}.

Wires together the real pipeline pieces:
  - ml_files/content_blocking.py  -> is_blocked()      (user-curated blocking)
  - scoring.py                    -> score_text()      (PLACEHOLDER ML scores)
  - ml_files/feed_logic.py        -> SessionTracker     (blur/tags/wellbeing)
  - ml_files/mood_predictor.py    -> MoodSession        (session mood / Analysis screen)
"""

from typing import Dict, List

from fastapi import FastAPI
from sklearn.feature_extraction.text import TfidfVectorizer

from ml_files.content_blocking import is_blocked
from ml_files.feed_logic import SessionTracker
from ml_files.mood_predictor import MoodSession, RunningStats
from models import ClassifyRequest, ClassifyResult, MoodResponse
from scoring import score_text

app = FastAPI()

# One SessionTracker/MoodSession/RunningStats per user_id, kept in memory for
# the life of the process. Lost on restart - fine for the demo; would need a
# real store (redis/db) once this needs to survive across server restarts.
session_trackers: Dict[str, SessionTracker] = {}
mood_sessions: Dict[str, MoodSession] = {}
mood_baselines: Dict[str, RunningStats] = {}


def get_tracker(user_id: str) -> SessionTracker:
    if user_id not in session_trackers:
        session_trackers[user_id] = SessionTracker()
    return session_trackers[user_id]


def get_mood_session(user_id: str) -> MoodSession:
    if user_id not in mood_sessions:
        mood_sessions[user_id] = MoodSession()
    return mood_sessions[user_id]


def get_mood_baseline(user_id: str) -> RunningStats:
    if user_id not in mood_baselines:
        mood_baselines[user_id] = RunningStats()
    return mood_baselines[user_id]


@app.post("/classify", response_model=List[ClassifyResult])
def classify(request: ClassifyRequest):
    tracker = get_tracker(request.user_id)

    # TEMPORARY: is_blocked()'s layer-3 similarity check needs a fitted
    # TF-IDF vectorizer. There's no trained model yet, so we fit one
    # on-the-fly from just this request's post texts + blocked terms.
    # Once the real model exists, load its saved vectorizer.pkl here
    # instead of fitting a throwaway one per request.
    corpus = [post.text for post in request.posts] + request.blocked_terms
    vectorizer = TfidfVectorizer().fit(corpus) if corpus else None

    results = []
    for post in request.posts:
        block_result = is_blocked(post.text, request.blocked_terms, vectorizer)

        if block_result["blocked"]:
            results.append(ClassifyResult(
                postId=post.id,
                should_blur=True,
                tags=["user-blocked"],
                wellbeing_score=None,
                reason=block_result["reason"],
            ))
            continue

        fake_scores = score_text(post.text)
        outcome = tracker.process_post(fake_scores)

        # TEMPORARY: MoodSession.add_post() also wants dwell_time_sec/
        # scroll_speed - real per-post behavioral signals (how long a post
        # was on screen, how fast the user was scrolling) that the Android
        # app doesn't measure yet. Feed plausible fixed placeholders for now
        # so the mood pipeline runs end-to-end; swap these for real
        # per-post values once the app tracks them.
        get_mood_session(request.user_id).add_post(
            fake_scores, dwell_time_sec=3.0, scroll_speed=100.0,
        )

        results.append(ClassifyResult(
            postId=post.id,
            should_blur=outcome["should_blur"],
            tags=outcome["tags"],
            wellbeing_score=outcome["wellbeing_score"],
            reason=None,
        ))

    return results


@app.get("/session/mood/{user_id}", response_model=MoodResponse)
def get_mood(user_id: str):
    session = get_mood_session(user_id)
    baseline = get_mood_baseline(user_id)
    result = session.predict(baseline)
    baseline.update(result["valence"])
    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
