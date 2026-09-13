# FeedIT Architecture

## Live pipeline (primary flow)

```
Android app (dummy raw posts, text only, no pre-set labels)
        │
        │  POST /classify
        ▼
   backend/main.py
        │
        ▼
   1. content_blocking.py -> is_blocked(text)
        │
        ├── blocked ──────────────────────────► return immediately
        │                                        (steps 2-3 skipped)
        │
        └── not blocked
                │
                ▼
   2. scoring.py -> score_text(text)
        produces the 6-label dict:
          { toxic, obscene, threat, insult, joy, sadness }

        **PLACEHOLDER** until the ML teammate delivers
        `model.pkl` + `vectorizer.pkl` into backend/model/.
        Marked clearly in scoring.py as the stub to swap out.
                │
                ▼
   3. feed_logic.py -> SessionTracker.process_post(scores)
        turns the 6-label dict into the real:
          - should_blur
          - tags
          - wellbeing_score
                │
                ▼
   backend sends the computed result back to the app
                │
                ▼
   Android app renders it (blur, tags, wellbeing score)
```

Every post the Android app sends goes through this exact sequence, live,
on every request. There is no pre-computation and no caching of labels —
`content_blocking.py` -> `scoring.py` -> `feed_logic.py` run in that order
for each `/classify` call.

### Step order is not optional

`is_blocked()` always runs first, on the raw text, before scoring. If a
post is blocked, the backend returns immediately and **does not** call
`score_text()` or `SessionTracker.process_post()`. Steps 2 and 3 only run
for posts that pass the block check.

## Critical constraint

**The vectorizer used in `content_blocking.py` / `scoring.py` MUST be the
exact same `vectorizer.pkl` that the toxicity model was trained with.
Never fit a separate vectorizer.** A mismatched vectorizer silently
produces garbage feature vectors — the model will still return scores,
they will just be meaningless. `vectorizer.pkl` and `model.pkl` are a
matched pair and must always be loaded from the same training run.

## Mocked login

Tapping "Connect Reddit" or "Connect Instagram" (on the login screen or
Profile) only flips a local `connected` flag in `MockAuthManager`. There
is no real OAuth flow, no network call, and no token exchange. `TokenStore`
exists to hold a fake/local token shape so the rest of the app can be wired
against a stable interface, not to talk to a real auth server.

The login screen itself is a one-time entry point (start destination of the
nav graph): entering a name and tapping Continue sets `UserSession.displayName`
(shown on Profile) and pops the login route off the back stack for good -
navigation from there on is Home/Analysis/Settings/Profile only.

## Fallback demo (separate, untouched, not part of the live flow)

`backend/fallback_demo/` (`index.html` + `mock_feed.json`) is a
**completely separate path** from the live pipeline described above:

- `mock_feed.json` is **pre-labeled** — it already contains
  `should_blur` / `tags` / `wellbeing_score` computed ahead of time, not
  produced by `content_blocking.py`, `scoring.py`, or `feed_logic.py`.
- It is served **standalone via `index.html` in a browser** — no
  backend server, no `/classify` endpoint, no pipeline involved at all.
- It is already tested and working, and is **kept untouched** as
  emergency insurance in case the live pipeline demo breaks on stage.
- It is **not** the primary data source and must not be wired into the
  Android app or the `/classify` endpoint. The Android app only ever
  talks to the live pipeline above.

Do not confuse `dummy_raw_posts.json` (raw text only, fed live through
the real pipeline) with `mock_feed.json` (pre-labeled, browser-only
fallback). They look similar but serve opposite purposes.

## Known issues to tune later

- **Insult weight may be too low.** In the wellbeing-score weighting
  inside `feed_logic.py`, insult currently contributes a weight of
  `0.15`. A post that is purely insulting (high `insult`, low everything
  else) may not accumulate enough weighted score to cross the blur
  threshold on its own. Needs tuning once real model scores are
  available.
- **TF-IDF similarity misses paraphrasing.** Any TF-IDF-based similarity
  check only catches posts that share vocabulary with known bad
  examples. A zero-overlap paraphrase of the same toxic content (same
  meaning, different words) will not be caught by TF-IDF similarity
  alone.
