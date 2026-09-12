"""
Real ML integration - calls the teammate's toxicity-model API (hosted via
ngrok during development) instead of generating fake scores.

IMPORTANT: that API only returns toxicity-style labels (toxic, severe_toxic,
obscene, threat, insult, identity_hate) - it has no emotion/sentiment model.
But feed_logic.py's wellbeing_score() and mood_predictor.py's valence math
both require joy/sadness. So joy/sadness below are a crude PLACEHOLDER
derived from the toxicity scores, NOT real model output - clearly marked
below. Swap them for a real emotion/sentiment model's output the moment
one exists; nothing else downstream needs to change either way.

severe_toxic/identity_hate from the API are read but not currently used
anywhere in the pipeline.
"""

import requests

ML_API_URL = "https://everybody-backrest-reclusive.ngrok-free.dev/predict"
REQUEST_TIMEOUT_SECONDS = 10


def score_text(text: str) -> dict:
    try:
        response = requests.post(
            ML_API_URL,
            json={"caption": text, "image_url": None, "audio_url": None},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        raw = response.json()
    except requests.RequestException as exc:
        raise RuntimeError(f"ML model API call failed: {exc}") from exc

    toxic = raw["toxic"]
    obscene = raw["obscene"]
    threat = raw["threat"]
    insult = raw["insult"]

    # PLACEHOLDER: see module docstring - no real emotion model yet.
    harm_signal = max(toxic, obscene, threat, insult)
    joy = round(max(0.0, 1.0 - harm_signal), 4)
    sadness = round((toxic + insult) / 2, 4)

    return {
        "toxic": toxic,
        "obscene": obscene,
        "threat": threat,
        "insult": insult,
        "joy": joy,
        "sadness": sadness,
    }
