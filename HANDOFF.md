# Handoff - FeedIT

> Updated 2026-09-13T03:01:54+05:30 by anshukanrar3021 (session 0912-2158, track 3)
> Read this first. The full log is cyhi-logs/session.md.

## Current state
Backend (FastAPI, backend/main.py): POST /classify (content_blocking.is_blocked -> scoring.score_text [FAKE] -> feed_logic.SessionTracker) and GET /session/mood/{user_id} (mood_predictor.MoodSession/RunningStats, auto-fed from the same scores /classify computes - no separate event endpoint). Both verified live via curl.
Android app: full MVVM. Retrofit/OkHttp/Gson (BackendApi, RetrofitClient, FeedRepository, MoodRepository). Jetpack Navigation Compose (FeedITApp.kt) - Home/Analysis/Settings bottom tabs, Profile via header button only. FeedScreen has infinite-scroll pagination (10/page) over a hardcoded 40-post list. PostCard: real Modifier.blur() + "Tap to reveal" pill, tag chips, inline wellbeing/blocked-status, like/comment/share icons (aesthetic only, no backend). AnalysisScreen: mood card (emoji/label/minutes, confidence/valence/arousal boxes, trend row, custom Canvas line chart) wired to GET /session/mood. ProfileScreen: mocked Reddit/YouTube connect status via MockAuthManager/TokenStore. Custom design system (AppColors/Color/Type/Theme.kt, LocalAppColors) used everywhere instead of raw hex/MaterialTheme defaults.

## Works
- /classify and /session/mood verified live end-to-end via curl against a running uvicorn server.
- Full clean `./gradlew clean :app:assembleDebug` succeeds repeatedly, including checkDebugDuplicateClasses.
- All screens/components have working @Preview functions with fake BackendApi impls (no real network needed to preview).

## Broken
- NO physical device/emulator available all session - navigation taps, real blur rendering, on-device behavior are UNVERIFIED. Every "works" claim about UI is compile-success + code review only, never an actual tap-through.
- RetrofitClient.BASE_URL is still a placeholder - app cannot reach the backend from a real phone until laptop's LAN IP is filled in.
- assets/dummy_raw_posts.json is still an empty stub - FeedScreen uses a hardcoded Kotlin list instead.
- scoring.py is fake (deterministic per-text-hash random scores), not the real ML model.
- Modifier.blur() only renders on API 31+; minSdk is 27, so pre-12 devices show fully readable text under the reveal pill (accepted tradeoff, see Decisions).
- User reported a "can't navigate back from Settings" glitch, traced to a real duplicate navigation-compose version conflict (2.9.0 vs 2.10.1) and fixed, but never confirmed fixed on-device (no device available) - only confirmed via checkDebugDuplicateClasses passing post-fix.

## Next 3 things
1. Get laptop's LAN IP (same WiFi as phone), set RetrofitClient.BASE_URL, rebuild+reinstall fresh APK, confirm real device reaches /classify (watch backend terminal for incoming request logs).
2. Move raw post data off the phone: real SQLite DB on the backend seeded with post data (id/text/media/type/url), new/changed endpoint so the app fetches pages from the server instead of a hardcoded list. Not started - only discussed conceptually.
3. Swap scoring.py's score_text() for the real trained model + vectorizer.pkl when ready. No other file should need to change per the existing architecture.

## Decisions (and why)
- Mood tracking reuses /classify's already-computed 6-label scores instead of a separate POST /session/event call - avoids a second round trip per post. dwell_time_sec/scroll_speed are still fixed placeholders (3.0, 100.0) since the app doesn't measure real per-post view time/scroll speed yet.
- PostCard blur uses real Modifier.blur() with NO opaque scrim fallback, per explicit user request ("actually blur it, don't just cover it") - accepted tradeoff of no hiding effect on API<31.
- Bottom-nav tab highlighting derived from NavDestination.hierarchy matching (Google's canonical pattern) instead of custom remembered/tracked state, to eliminate a suspected source of nav drift.
- /classify's request contract (client sends post text) intentionally left unchanged rather than redesigned into a bare "give me next page" endpoint - deferred until the DB decision above is actually built.

## Don't retry
- Don't add a second navigation-compose (or any) dependency under a different alias/version_ref to "fix" a build error without first checking libs.versions.toml for an existing entry - caused a real duplicate-classpath conflict (2.9.0 vs 2.10.1 of the same artifact) that broke navigation at runtime.
- Don't `import androidx.compose.foundation.layout.weight` as a top-level import in this project - it collides with an internal RowColumnParentData.weight symbol and fails to compile in this Compose version. Just call `.weight(...)` unqualified inside Row/Column scope, no import needed.
- Don't use `Icons.AutoMirrored.Filled.ArrowForward` / that import path here - unresolved in this project's material-icons-extended version. Use `Icons.Filled.ArrowForward` (deprecated but compiles) instead.
- Don't treat a clean `:app:compileDebugKotlin`/`assembleDebug` as proof the app behaves correctly on-device - no emulator/device has been reachable this whole session (no adb devices, no emulator installed).
