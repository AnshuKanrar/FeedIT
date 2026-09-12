"""
FastAPI server exposing POST /classify.

Wires together the real pipeline pieces:
  - ml_files/content_blocking.py  -> is_blocked()      (user-curated blocking)
  - scoring.py                    -> score_text()      (PLACEHOLDER ML scores)
  - ml_files/feed_logic.py        -> SessionTracker     (blur/tags/wellbeing)
"""

from typing import Dict, List

from fastapi import FastAPI
from sklearn.feature_extraction.text import TfidfVectorizer

from ml_files.content_blocking import is_blocked
from ml_files.feed_logic import SessionTracker
from models import ClassifyRequest, ClassifyResult
from scoring import score_text

app = FastAPI()

# One SessionTracker per user_id, kept in memory for the life of the process.
# Lost on restart - fine for the demo; would need a real store (redis/db)
# once this needs to survive across server restarts.
session_trackers: Dict[str, SessionTracker] = {}


def get_tracker(user_id: str) -> SessionTracker:
    if user_id not in session_trackers:
        session_trackers[user_id] = SessionTracker()
    return session_trackers[user_id]


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

        results.append(ClassifyResult(
            postId=post.id,
            should_blur=outcome["should_blur"],
            tags=outcome["tags"],
            wellbeing_score=outcome["wellbeing_score"],
            reason=None,
        ))

    return results


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
