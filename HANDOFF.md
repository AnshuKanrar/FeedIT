# Handoff - FeedIT

> Updated 2026-09-13T10:35:20+05:30 by anshukanrar3021 (session 0912-2158, track 3)
> Read this first. The full log is cyhi-logs/session.md.

## Current state
Backend (FastAPI): four endpoints - POST /classify, POST /feed (server-owned pagination over dummy_posts.json), GET /session/mood/{user_id}, GET /session/attention/{user_id} (new this session - reels-scrolled-per-minute tracking, see attention_tracker.py). All four share state via classify_one() and per-user in-memory trackers. blur_threshold/similarity_threshold are now user-tunable end to end from the Android Settings screen through to content_blocking.is_blocked()/feed_logic.SessionTracker.
Android: full MVVM, server-owned feed (zero hardcoded content), one-time persisted login (SharedPreferences), real Settings screen (blocked terms + 2 thresholds, persisted), Analysis screen now has BOTH a Mood card and an Attention card (bar chart of scrolls/min). Real 500-post dataset merged from teammate's branch (git push/pull done, clean merge). Docs: ARCHITECTURE.md (backend), APP_TECHNICAL_OVERVIEW.md (new - full Android technical writeup), README.md (new - short project overview), HANDOFF.md (this file).

## Works
- All 4 backend endpoints verified together via direct Python calls (score_text mocked) - no regressions across the classify_one() refactor.
- Real ML API (ngrok) confirmed live and reachable multiple times this session.
- Full clean `./gradlew clean :app:assembleDebug` passes repeatedly.
- git push succeeded after a real branch divergence (teammate's dataset commits vs local feature work) - resolved via explicit merge, verified before pushing.

## Broken / recurring issue
- **PostCard.kt's `import coil.compose.AsyncImage` has been silently replaced with a broken `TODO()` stub THREE separate times this session** (each time fixed). Root cause unconfirmed - suspected Android Studio "create function" quick-fix being accepted when Coil's import shows temporarily unresolved before a Gradle sync completes. User was told: if AsyncImage shows unresolved in the IDE, do NOT accept an auto-generated stub - just wait for/trigger a Gradle sync. Worth checking this file first if images ever stop rendering again.
- Still no confirmed clean successful on-device feed load reported back this session, despite many individual bugs being found and fixed (cleartext, INTERNET permission, stale server, dataset field mismatch, the AsyncImage regression x3, port-8000-already-in-use from duplicate uvicorn processes across terminal tabs, fresh-terminal-tab-missing-venv-activation).
- User's dev workflow friction: multiple Android Studio terminal tabs, easy to lose track of which one has the venv activated / a server already running on port 8000. Recommend they stick to ONE terminal tab for the backend for the rest of the event.

## Next 3 things
1. Get a real confirmed successful on-device test end to end (feed loads, images render, blur/reveal works, Analysis shows both cards) - has not happened yet this whole session.
2. Watch for the AsyncImage regression recurring a 4th time.
3. Nothing else major outstanding - core feature set (feed, blocking, mood, attention, settings, login, profile) is functionally complete per repeated backend-side verification; remaining risk is entirely on-device/environment, not code logic.

## Decisions (and why)
- attention_tracker.py follows the exact same pattern as mood_predictor.py (per-user in-memory tracker, OLS trend slope, auto-fed from classify_one() with no extra round trip) for consistency.
- Attention's trend color mapping is deliberately INVERTED from Mood's (rising scroll rate = bad/red, falling = good/green) since they measure opposite-direction concepts - documented in code comments so it isn't "fixed" to match Mood's mapping by mistake later.
- Kept per-tab terminal/venv friction as a user workflow issue to flag, not something to solve in code (e.g. did not build a shell script wrapper) - out of scope, and the user has been managing it themselves each time.

## Don't retry
(carried forward, still true)
- Don't add a second navigation-compose (or any) dependency under a different alias/version_ref without checking libs.versions.toml first.
- Don't `import androidx.compose.foundation.layout.weight` as a top-level import here - collides with an internal symbol, fails to compile.
- Don't use `Icons.AutoMirrored.Filled.ArrowForward` here - unresolved in this project's material-icons-extended version. Use `Icons.Filled.ArrowForward`.
- Don't treat a clean compile/assembleDebug as proof of on-device behavior.
- Don't assume `uvicorn` picks up backend .py changes automatically without `--reload` and a restart.
- Don't assume a teammate's real dataset matches whatever field names placeholder code assumed - check one real sample first.
- NEW: Don't assume PostCard.kt's Coil import is intact just because it compiled recently - it has regressed 3 times. Spot-check it if images ever break again.
- NEW: A curl to the user's LAN IP (e.g. 172.27.55.213) from this sandbox will always hang/fail - that address is only reachable from the user's own network, never from here. Don't waste time testing it; only the user can verify their own backend's live reachability.
