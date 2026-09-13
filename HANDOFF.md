# Handoff - FeedIT

> Updated 2026-09-13T05:51:35+05:30 by anshukanrar3021 (session 0912-2158, track 3)
> Read this first. The full log is cyhi-logs/session.md.

## Current state
Backend (FastAPI, backend/main.py): three endpoints sharing one classify_one() helper - POST /classify, POST /feed (server-owned pagination over backend/dummy_posts.json, re-read fresh per request), GET /session/mood/{user_id}. scoring.py calls the real ML teammate's toxicity API (ngrok); that API has no joy/sadness so those are a clearly-marked placeholder derived from the harm score. dummy_posts.json holds the friend's real 500-post dataset (200 with real image_url values, 0 with audio).
Android app: fully server-owned feed (zero hardcoded post content anywhere). New this session: a one-time LoginScreen is now the nav start destination - hardcoded FontFamily.Serif "FeedIT" wordmark, a name field that sets UserSession.displayName (shown on Profile instead of the raw backend userId), and two purely cosmetic "Connect" toggles (Reddit + Instagram, swapped from Reddit+YouTube - reuses MockAuthManager, no real OAuth). Continuing pops Login off the back stack for good and lands on Home; Scaffold's top/bottom bars are hidden while on the login route. PostCard renders images via Coil, blurs+hides them together with text when a post is flagged.

## Works
- /classify, /feed, /session/mood verified consistent together via direct Python calls with score_text mocked.
- Full clean `./gradlew clean :app:assembleDebug` passes repeatedly, including checkDebugDuplicateClasses.
- Real 500-post dataset parses correctly (caption/image_url/audio_url + integer id, normalized in main.py).
- All screens have working @Preview functions with realistic sample data (no live server/ML API needed to sanity-check UI).

## Broken
- Caught and fixed a real regression THIS session: PostCard.kt's `import coil.compose.AsyncImage` had been replaced (by an external edit, not a request) with a local stub function doing `TODO()` - would have crashed instantly on any post with an image. Restored the real import. Worth double-checking PostCard.kt renders images correctly on next device test, since this was silently broken for at least one prior turn.
- ML teammate's ngrok tunnel is down (502 Bad Gateway) - blocks real end-to-end testing, not our bug.
- Still no confirmed successful on-device feed load this whole session - real bugs were found and fixed along the way (CLEARTEXT blocking, missing INTERNET permission, stale un-restarted backend, dataset field mismatch, the AsyncImage regression above) but nobody has reported an actual clean successful run yet.
- Settings screen still a stub - blocked_terms has no real UI, always empty.
- UserSession.displayName is NOT persisted (resets to "Guest" on process death) - intentional for now, "one-time login" was read as "once per app session," not "once ever." Revisit if they want it to survive restarts.

## Next 3 things
1. Get the ML API back up, then one real on-device pass: fresh install, restart backend, confirm feed loads with real photos+captions+scores AND the login screen appears first.
2. Build a real Settings screen so blocked_terms comes from actual user input.
3. Decide if UserSession.displayName should persist (SharedPreferences) across app restarts, or "one-time" truly just means once per session as currently built.

## Decisions (and why)
- joy/sadness placeholder formula: user's explicit pick among 3 presented options, since the real ML API has no emotion model.
- dummy_posts.json stays a flat re-read-per-request file, not a DB - cheap at 500 entries, and the friend's git commits take effect with zero backend restart (only backend CODE changes need a restart).
- /classify and /feed share one classify_one() helper so behavior can't drift between them.
- Login screen wordmark uses FontFamily.Serif (a built-in system family) rather than a bundled custom font file, per "hardcode the font" - no font asset infrastructure exists in the project, and this satisfies "stylish" without adding one.
- Reddit+Instagram (not Reddit+YouTube) per explicit correction mid-request; TokenStore/MockAuthManager/ARCHITECTURE.md all updated to match.

## Don't retry
- Don't add a second navigation-compose (or any) dependency under a different alias/version_ref without checking libs.versions.toml first - caused a real duplicate-classpath conflict (2.9.0 vs 2.10.1) that broke navigation at runtime.
- Don't `import androidx.compose.foundation.layout.weight` as a top-level import here - collides with an internal RowColumnParentData.weight symbol, fails to compile. Call `.weight(...)` unqualified inside Row/Column scope.
- Don't use `Icons.AutoMirrored.Filled.ArrowForward` / that import path here - unresolved in this project's material-icons-extended version. Use `Icons.Filled.ArrowForward` (deprecated but compiles).
- Don't treat a clean compile/assembleDebug as proof of on-device behavior - confirm with an actual device/emulator when available.
- Don't assume `uvicorn` picks up backend .py changes automatically - must be manually killed and restarted every time. Caused multiple "still broken" reports that were actually a stale server. Consider `--reload` for the rest of the event.
- Don't assume a teammate's real dataset matches whatever field names placeholder code assumed - always check one real sample entry first.
- When the ML API errors (502/timeout), it's a clean Python exception naming the failing URL - that means "check the other team's server," not a bug here.
- Watch for external edits silently reverting working code to a broken stub (happened to PostCard.kt's AsyncImage import) - when a file changed-on-disk notice shows something that looks broken (a TODO(), a removed import), say so and verify before assuming it's intentional.
