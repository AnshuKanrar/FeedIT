"""
PLACEHOLDER scoring - no trained model exists yet.

score_text() returns fake toxic/obscene/threat/insult/joy/sadness scores so
the rest of the pipeline (content_blocking.py, feed_logic.py) can be wired
up and tested end-to-end before the real model is ready.

Replace the body of score_text() with a call into the real trained model
(+ its saved vectorizer.pkl) once that exists. Everything downstream
(SessionTracker, blur/tag/wellbeing logic) already expects this exact
6-key dict shape and won't need to change.
"""

import random

LABELS = ["toxic", "obscene", "threat", "insult", "joy", "sadness"]


def score_text(text: str) -> dict:
    # Deterministic per-text "randomness" (same text -> same fake scores
    # within a single server run) so repeated demo/test calls stay consistent.
    rng = random.Random(hash(text) & 0xFFFFFFFF)
    return {label: round(rng.random(), 3) for label in LABELS}
