# FeedIT

A social feed that classifies every post in real time — toxicity/harm scoring, user-defined
content blocking, and session-level mood + attention-span tracking — before it ever reaches
the screen.

Built for Can You Hack It? (Track 3).

## Stack

- **Android app**: Kotlin, Jetpack Compose, MVVM, Retrofit, Navigation Compose, Coil
- **Backend**: Python, FastAPI
- **ML**: real toxicity model (via teammate's hosted API) + session analytics (mood, attention)

## Running it

**Backend:**
```bash
cd backend
source .venv/bin/activate
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

**Android app:** open in Android Studio, set your machine's LAN IP in
`app/src/main/java/com/example/distll/network/RetrofitClient.kt` (`BASE_URL`), then run on a
device on the same WiFi network.

## How it works, in one line

Phone sends raw post text → backend runs it through blocking → ML scoring → blur/tag/wellbeing
logic → sends back the verdict. The phone never runs any ML itself — it only renders whatever
JSON the backend returns.

## Docs

- **`ARCHITECTURE.md`** — the backend pipeline in detail (blocking → scoring → feed logic → mood
  → attention), and why steps run in that order.
- **`APP_TECHNICAL_OVERVIEW.md`** — how the Android app itself is built (Compose, Retrofit, MVVM,
  navigation, every screen).
- **`HANDOFF.md`** — current state: what works, what's broken, what's next.
