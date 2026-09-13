"""
FastAPI server exposing POST /classify, POST /feed, and GET /session/mood/{user_id}.

Wires together the real pipeline pieces:
  - ml_files/content_blocking.py  -> is_blocked()      (user-curated blocking)
  - scoring.py                    -> score_text()      (real ML API call)
  - ml_files/feed_logic.py        -> SessionTracker     (blur/tags/wellbeing)
  - ml_files/mood_predictor.py    -> MoodSession        (session mood / Analysis screen)
"""

import json
from pathlib import Path
from typing import Dict, List

from fastapi import FastAPI
from sklearn.feature_extraction.text import TfidfVectorizer

from ml_files.content_blocking import is_blocked
from ml_files.feed_logic import SessionTracker
from ml_files.mood_predictor import MoodSession, RunningStats
from models import (
    ClassifyRequest,
    ClassifyResult,
    FeedPost,
    FeedRequest,
    FeedResponse,
    MoodResponse,
)
from scoring import score_text

app = FastAPI()

DUMMY_POSTS_PATH = Path(__file__).parent / "dummy_posts.json"

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


def load_dummy_posts() -> List[dict]:
    """
    Re-read dummy_posts.json fresh on every call (not cached at startup) -
    500 small entries is cheap to parse, and it means whoever's committing
    the dataset can update the file and the very next request serves the
    new data, with no backend restart needed.
    """
    if not DUMMY_POSTS_PATH.exists():
        return []
    with open(DUMMY_POSTS_PATH, "r") as f:
        return json.load(f)


def classify_one(
    post_id: str,
    text: str,
    blocked_terms: List[str],
    vectorizer,
    user_id: str,
    blur_threshold: float = 0.5,
    similarity_threshold: float = 0.3,
) -> dict:
    """
    The one true per-post pipeline: is_blocked() -> score_text() ->
    SessionTracker -> MoodSession. Shared by /classify and /feed so both
    endpoints always behave identically - never duplicate this logic.

    blur_threshold/similarity_threshold are user-tunable (Settings screen);
    both content_blocking.is_blocked() and feed_logic.SessionTracker already
    accept/expose these as parameters, so no logic there needed to change.
    """
    block_result = is_blocked(text, blocked_terms, vectorizer, similarity_threshold=similarity_threshold)

    if block_result["blocked"]:
        return {
            "postId": post_id,
            "text": text,
            "should_blur": True,
            "tags": ["user-blocked"],
            "wellbeing_score": None,
            "reason": block_result["reason"],
        }

    fake_scores = score_text(text)
    tracker = get_tracker(user_id)
    tracker.blur_threshold = blur_threshold
    outcome = tracker.process_post(fake_scores)

    # TEMPORARY: MoodSession.add_post() also wants dwell_time_sec/
    # scroll_speed - real per-post behavioral signals (how long a post
    # was on screen, how fast the user was scrolling) that the Android
    # app doesn't measure yet. Feed plausible fixed placeholders for now
    # so the mood pipeline runs end-to-end; swap these for real
    # per-post values once the app tracks them.
    get_mood_session(user_id).add_post(fake_scores, dwell_time_sec=3.0, scroll_speed=100.0)

    return {
        "postId": post_id,
        "text": text,
        "should_blur": outcome["should_blur"],
        "tags": outcome["tags"],
        "wellbeing_score": outcome["wellbeing_score"],
        "reason": None,
    }


@app.post("/classify", response_model=List[ClassifyResult])
def classify(request: ClassifyRequest):
    # TEMPORARY: is_blocked()'s layer-3 similarity check needs a fitted
    # TF-IDF vectorizer. There's no trained model yet, so we fit one
    # on-the-fly from just this request's post texts + blocked terms.
    # Once the real model exists, load its saved vectorizer.pkl here
    # instead of fitting a throwaway one per request.
    corpus = [post.text for post in request.posts] + request.blocked_terms
    vectorizer = TfidfVectorizer().fit(corpus) if corpus else None

    results = [
        classify_one(
            post.id, post.text, request.blocked_terms, vectorizer, request.user_id,
            blur_threshold=request.blur_threshold,
            similarity_threshold=request.similarity_threshold,
        )
        for post in request.posts
    ]
    return [ClassifyResult(**r) for r in results]


@app.post("/feed", response_model=FeedResponse)
def get_feed(request: FeedRequest):
    all_posts = load_dummy_posts()
    page_raw = all_posts[request.offset: request.offset + request.limit]

    # Defensive: skip/patch malformed entries rather than crashing the whole
    # page over one bad row in a 500-entry dataset someone else is committing.
    # The real dataset uses the ML API's own field names (caption/image_url/
    # audio_url) and integer ids - normalize both here. audio_url isn't
    # rendered anywhere yet (no audio player in the app); image_url is.
    page = [
        {
            "id": str(raw["id"]) if raw.get("id") is not None else f"unknown-{request.offset + i}",
            "text": raw.get("caption") or raw.get("text") or "",
            "image_url": raw.get("image_url"),
        }
        for i, raw in enumerate(page_raw)
    ]

    corpus = [p["text"] for p in page] + request.blocked_terms
    vectorizer = TfidfVectorizer().fit(corpus) if corpus else None

    results = [
        {
            **classify_one(
                p["id"], p["text"], request.blocked_terms, vectorizer, request.user_id,
                blur_threshold=request.blur_threshold,
                similarity_threshold=request.similarity_threshold,
            ),
            "image_url": p["image_url"],
        }
        for p in page
    ]

    has_more = request.offset + request.limit < len(all_posts)
    return FeedResponse(posts=[FeedPost(**r) for r in results], has_more=has_more)


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
