# FeedIT Android App — Technical Overview

This document explains **how the Android app is actually built**: every library, every
architectural layer, every screen, and how data flows from a network response to
pixels on screen. It's a companion to `ARCHITECTURE.md` (which covers the backend
pipeline and why certain decisions were made) — this file is about the Android
client's internals specifically.

Package root: `com.example.distll` (the app's application ID/theme live under
`com.example.feedit` for historical reasons — that split is harmless, just two
package roots in one app).

---

## 1. Tech stack at a glance

| Concern | Library / Tool |
|---|---|
| UI toolkit | Jetpack Compose (declarative UI, no XML layouts) |
| Architecture pattern | MVVM (Model–View–ViewModel) |
| Networking | Retrofit 2 + OkHttp + Gson |
| Navigation | Jetpack Navigation Compose |
| Async | Kotlin Coroutines (`viewModelScope`, `suspend` functions) |
| Image loading | Coil (`coil-compose`) |
| Local persistence | `SharedPreferences` (via small custom wrapper objects) |
| State management | Compose `State`, `StateFlow`, `mutableStateOf` |

Every dependency and its exact version lives in `gradle/libs.versions.toml` (the
Gradle **version catalog** — a single source of truth so `app/build.gradle.kts`
never hardcodes a version string).

---

## 2. Jetpack Compose — the UI toolkit

Compose replaces Android's old XML-layout system with plain Kotlin functions.
Every screen and every small piece of UI (a button, a card, a text field) is a
**composable function** — a function annotated `@Composable` that describes what
should be on screen, not how to mutate views imperatively.

```kotlin
@Composable
fun PostCard(post: FeedPost, modifier: Modifier = Modifier) { ... }
```

### 2.1 State — what makes the UI update

Compose re-runs (**recomposes**) a composable function whenever the state it reads
changes. Three flavors of state show up throughout this app:

- **`mutableStateOf(...)`** — a single observable value. Used inside `object`
  singletons like `UserSession` and `SettingsStore` so that *any* composable reading
  `UserSession.displayName` automatically recomposes the moment it changes, from
  anywhere in the app, with no ViewModel plumbing needed for that one value.
- **`remember { ... }`** — caches a value across recompositions of the *same*
  composition (so it isn't recreated every frame), but is lost if the composable
  leaves the screen and comes back (e.g. rotating, or navigating away and back).
  Used for local, throwaway UI state like a text field's current draft value.
- **`rememberSaveable { ... }`** — like `remember`, but survives configuration
  changes (rotation) and process death by saving into the instance-state bundle.
  Used for things like `PostCard`'s "has this specific post been revealed" flag —
  keyed per `post.postId` so each card's reveal state is independent.
- **`StateFlow`** (from Kotlin coroutines) — used inside every `ViewModel` to expose
  a single immutable `UiState` data class. The View layer collects it with
  `collectAsState()` and Compose handles the recomposition automatically.

### 2.2 Side effects

Composable functions must stay "pure" (no direct network calls, no mutating things
outside Compose) — anything that needs to *do* something (fire a network request,
start observing something) goes through an effect handler:

- **`LaunchedEffect(key1, key2, ...)`** — runs a coroutine when the composable
  first enters composition, and re-runs it if any key changes. Used everywhere a
  screen needs to trigger a load on entry: `FeedScreen` uses
  `LaunchedEffect(userId, blockedTerms) { viewModel.start(...) }`.
- **`snapshotFlow { ... }`** — turns a piece of Compose state into a `Flow`, so it
  can be observed with normal Flow operators inside a coroutine. `FeedScreen` uses
  this to watch the `LazyColumn`'s scroll position and trigger the next page load
  once the user scrolls near the bottom (see §6.2).

### 2.3 CompositionLocal — theme values without passing params everywhere

`LocalAppColors` is a **`CompositionLocal`** — a way to make a value (here, the
app's color palette) implicitly available to every composable below it in the
tree, without threading it through every function's parameter list. Any composable
can read `LocalAppColors.current.primary` and get the right color for the current
theme (light/dark) automatically.

### 2.4 Theming — `com.example.feedit.ui.theme`

- **`Color.kt`** — raw hex color constants (`Teal700`, `Cream50`, `Ink900`, etc.) —
  just values, no meaning attached yet.
- **`AppColors.kt`** — a `data class AppColors` giving each raw color a *semantic*
  name (`primary`, `background`, `wellbeingPositive`, `bottomNavBar`, ...), plus
  `LightAppColors`/`DarkAppColors` instances and the `LocalAppColors`
  `CompositionLocal` that exposes whichever one is active.
- **`Type.kt`** — the app's `Typography` scale (`headlineLarge`, `bodyMedium`, etc.)
  — all `FontFamily.Default` (system font) except the login screen's wordmark,
  which hardcodes `FontFamily.Serif` directly for a distinct one-off look.
- **`Theme.kt`** — `FeedITTheme { ... }`, the root composable every screen's
  `@Preview` wraps itself in. It picks Light/Dark `MaterialTheme.colorScheme` (with
  optional Android 12+ dynamic color) **and** provides `LocalAppColors` — so both
  the standard Material3 components (`Card`, `Button`, `Slider`, ...) and this
  app's custom semantic colors stay in sync.

Any custom FeedIT-specific component (`PostCard`, `BottomNavBar`, `TopHeaderBar`,
the Login/Settings screens) reads colors from `LocalAppColors`, not
`MaterialTheme.colorScheme` directly — that's the whole reason `AppColors.kt`
exists as a separate layer.

---

## 3. Networking — Retrofit, OkHttp, Gson

### 3.1 `RetrofitClient.kt` — the singleton that builds everything

```kotlin
object RetrofitClient {
    private const val BASE_URL = "http://172.27.55.213:8000/"
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)   // logs every request/response body
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val backendApi: BackendApi by lazy { retrofit.create(BackendApi::class.java) }
}
```

- **`BASE_URL`** — the one line that has to change whenever the backend's address
  changes (a laptop's LAN IP is not stable — see the many "IP changed" incidents in
  `HANDOFF.md`). This is a **known, deliberate weak point**: it's a hardcoded
  constant, not a build-time or runtime-configurable value.
- **`HttpLoggingInterceptor`** — logs the full request/response JSON to Logcat for
  every call. This is what let debugging sessions confirm exactly what the backend
  returned without needing a separate tool.
- **`GsonConverterFactory`** — Retrofit doesn't know how to turn a Kotlin object
  into JSON (or back) on its own; Gson does that conversion. Every request/response
  data class relies on `@SerializedName("snake_case_key")` annotations to map
  between Kotlin's camelCase convention and the backend's Python/JSON snake_case
  convention (e.g. `@SerializedName("should_blur") val shouldBlur: Boolean`).
- **`backendApi by lazy { ... }`** — the actual `Retrofit`-generated implementation
  of the `BackendApi` interface is only built the first time it's accessed, then
  reused for the app's whole lifetime (it's expensive to construct, cheap to reuse).

### 3.2 `BackendApi.kt` — the interface Retrofit implements

This is a plain Kotlin interface; Retrofit reads its annotations at runtime and
generates a real implementation.

```kotlin
interface BackendApi {
    @POST("classify")
    suspend fun classify(@Body request: ClassifyRequest): List<ClassificationResult>

    @POST("feed")
    suspend fun getFeed(@Body request: FeedRequest): FeedResponse

    @GET("session/mood/{userId}")
    suspend fun getMood(@Path("userId") userId: String): MoodResponse

    @GET("session/attention/{userId}")
    suspend fun getAttention(@Path("userId") userId: String): AttentionResponse
}
```

- **`suspend fun`** — every call is a coroutine suspend function. Retrofit handles
  moving the actual HTTP I/O off the main thread automatically; callers just need
  to be inside a coroutine (a `viewModelScope.launch { }` block).
- **`@POST`/`@GET`/`@Body`/`@Path`** — Retrofit annotations describing the HTTP verb,
  URL path, and where each parameter goes (request body vs. URL path segment).
- Each endpoint here maps 1:1 to a FastAPI route in `backend/main.py` — see
  `ARCHITECTURE.md` for what each one actually computes server-side.

Also in this file: the **request DTOs** (`ClassifyRequest`, `FeedRequest`) — plain
data classes that exist purely to describe the shape Retrofit/Gson should serialize
to JSON. They deliberately mirror the backend's Pydantic models field-for-field
(including default values like `blurThreshold: Float = 0.5f`, matching the
backend's `blur_threshold: float = 0.5`).

### 3.3 Repositories — the layer between ViewModels and the network

A **Repository** wraps `BackendApi` calls in `Result<T>` (via `runCatching { }`),
so a failed network call becomes a typed `Result.failure` instead of an uncaught
exception, and callers just do `.onSuccess { }.onFailure { }`.

- **`FeedRepository`** — `classifyPosts(...)` and `getFeed(...)`.
- **`MoodRepository`** — `getMood(...)` and `getAttention(...)` (both session-
  analytics endpoints the Analysis screen needs).

Repositories are plain classes (not singletons), constructed with a default
`api: BackendApi = RetrofitClient.backendApi` parameter — this is what makes them
trivially fakeable in Compose Previews (see §6.5): a preview just constructs the
repository with a fake `BackendApi` implementation instead of the real one, and
every ViewModel/View built on top of it renders without touching the network.

---

## 4. MVVM — how the layers fit together

```
Backend (FastAPI)
      │  JSON over HTTP
      ▼
BackendApi (Retrofit interface)
      │
      ▼
Repository (wraps calls in Result<T>)
      │
      ▼
ViewModel (owns UiState, calls repository inside viewModelScope.launch)
      │  StateFlow<UiState>
      ▼
Composable "Screen" (collectAsState(), renders — no logic of its own)
```

Every screen in this app follows this exact shape. The **View** (the `@Composable
XyzScreen` function) is intentionally "dumb" — its only jobs are:
1. Get (or default-construct) its `ViewModel` via `viewModel()`.
2. `collectAsState()` the ViewModel's `uiState`.
3. Trigger a `LaunchedEffect` to kick off loading.
4. Render whatever `uiState` currently holds (loading / error / data).

All the actual decision-making (what to call, how to interpret the response, what
counts as an error) lives in the ViewModel. This split is why, for example,
`FeedScreen.kt` has zero network imports in its main composable — only its
Preview's fake `BackendApi` does.

### ViewModel roster

| ViewModel | Backs | Owns |
|---|---|---|
| `FeedViewModel` | `FeedScreen` | Paginated `FeedPost` list, offset tracking, infinite scroll state |
| `AnalysisViewModel` | `AnalysisScreen` | Mood + Attention data, loaded in parallel |
| `ProfileViewModel` | `ProfileScreen` | Mocked connected-platform list |

(`LoginScreen` and `SettingsScreen` deliberately have **no ViewModel** — they
read/write directly through `UserSession`/`SettingsStore`, since those are already
observable global singletons and there's no network round trip involved in either
screen. Adding a ViewModel there would just be an extra layer forwarding calls.)

---

## 5. Navigation — Jetpack Navigation Compose

### 5.1 The pieces

- **`Routes.kt`** — a plain `object` of `String` route constants (`"login"`,
  `"home"`, `"analysis"`, `"settings"`, `"profile"`). The single source of truth so
  a route name is never typed twice.
- **`FeedITNavHost.kt`** — the actual `NavHost { composable(route) { Screen() } }`
  graph. Maps each route to the screen that should render there.
- **`FeedITApp.kt`** — the top-level composable: builds the `NavController`, wraps
  everything in a `Scaffold` (top bar + bottom bar + content), and decides the
  **start destination** before the graph is even built.

### 5.2 Login as a one-time start destination

This is the trickiest part of the nav setup, so it's worth spelling out exactly how
it works:

```kotlin
val startDestination = remember {
    UserSession.restore(context)
    SettingsStore.restore(context)
    if (UserSession.isLoggedIn) Routes.HOME else Routes.LOGIN
}
```

- `remember { }` with no keys means this block runs **exactly once** per process
  (the first time `FeedITApp` composes), not on every recomposition.
- `UserSession.restore(context)` reads a `SharedPreferences` boolean synchronously
  — fast enough that doing it inline, before the `NavHost` is constructed, produces
  zero visible flash of the wrong start screen.
- The computed `startDestination` string is then passed into `FeedITNavHost`, which
  uses it as `NavHost(startDestination = startDestination) { ... }` instead of a
  hardcoded route.

When the user taps "Continue" on the login screen:

```kotlin
UserSession.login(context, name)                 // persist to SharedPreferences
navController.navigate(Routes.HOME) {
    popUpTo(Routes.LOGIN) { inclusive = true }     // remove Login from the back stack
}
```

`popUpTo(..., inclusive = true)` deletes the login destination from the back stack
entirely — pressing the system back button from Home will never return to Login.
Combined with the persisted `isLoggedIn` flag, this makes login genuinely one-time:
even force-closing and reopening the app skips straight to Home.

### 5.3 Bottom tab navigation

`BottomNavBar` (a pure, stateless component — see §6) is driven from outside by
`FeedITApp`:

```kotlin
val selectedTab = NavigationContents.entries.find { tab ->
    currentDestination?.hierarchy?.any { it.route == tab.route } == true
} ?: NavigationContents.HOME
```

This reads the **current destination's hierarchy** (Navigation Compose's own
concept of "what destination is active, including any parent graphs") to decide
which tab is highlighted — rather than tracking "which tab is selected" as separate
app state. That distinction matters: state that's *derived* from the navigation
graph can never drift out of sync with what's actually on screen, whereas a
separately-tracked "current tab" variable could theoretically disagree with reality
after some edge-case navigation.

Tapping a tab calls a small extension function:

```kotlin
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

This is the standard Android bottom-nav recipe: `launchSingleTop` stops a second
copy of the same tab being pushed if you tap the tab you're already on;
`popUpTo(start) { saveState = true }` + `restoreState = true` together mean
switching away from and back to a tab restores its scroll position/state instead
of rebuilding it from scratch.

### 5.4 Login and Profile are *not* tabs

Login has no bottom bar or top header at all (`FeedITApp` conditionally hides both
when `currentDestination?.route == Routes.LOGIN`, since it's a distinct full-screen
entry point). Profile is reachable only via the profile icon in `TopHeaderBar` — it
deliberately isn't one of the three bottom tabs, so it has no "selected" state of
its own in the tab bar (whichever tab you were last on stays highlighted
underneath it).

---

## 6. Screens and components, one by one

### 6.1 `LoginScreen.kt`

A one-time entry screen with no ViewModel. Local state: just the text field's
current draft (`rememberSaveable { mutableStateOf("") }`).

- The "FeedIT" wordmark hardcodes `FontFamily.Serif` directly on that one `Text` —
  a deliberate one-off, not part of the app's normal type scale.
- Two "Connect" toggles (Reddit, Instagram) call `MockAuthManager.connect()`/
  `disconnect()` directly — purely cosmetic, no real OAuth (see §8).
- "Continue" calls the `onContinue: (name: String) -> Unit` callback that
  `FeedITNavHost` supplies (see §5.2) — the screen itself has no idea navigation or
  persistence exist; it just reports the entered name upward.

### 6.2 `FeedScreen.kt` + `FeedViewModel.kt` + `PostCard.kt`

The most complex screen. `FeedViewModel` owns:

```kotlin
data class FeedUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val posts: List<FeedPost> = emptyList(),
    val endReached: Boolean = false,
    val error: String? = null,
)
```

**Pagination**: the ViewModel tracks its own `offset` integer. `start()` resets it
to 0 and loads the first page; `loadNextPage()` asks the repository for
`offset until offset+pageSize`, appends the results to the existing list, and
advances `offset`. The server tells it when to stop via `hasMore` on the response.

**Infinite scroll trigger** (in the View, not the ViewModel):

```kotlin
LaunchedEffect(listState) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { lastVisibleIndex ->
            if (lastVisibleIndex != null && lastVisibleIndex >= postCount - LOAD_MORE_THRESHOLD) {
                viewModel.loadNextPage()
            }
        }
}
```

This watches the `LazyColumn`'s scroll state and fires `loadNextPage()` once the
user has scrolled within `LOAD_MORE_THRESHOLD` (3) items of the end of what's
already loaded — the classic "Instagram-style" infinite scroll pattern.

**`PostCard.kt`** renders one `FeedPost`. Key pieces:
- **Image**: `AsyncImage(model = post.imageUrl, ...)` from Coil — Coil handles
  fetching, decoding, memory/disk caching, and placeholder/error states internally;
  the app just gives it a URL.
- **Hiding flagged content**: when `post.shouldBlur && !revealed`, the image gets a
  real `Modifier.blur(14.dp)` (works on API 31+ only — see the code comment for
  why there's deliberately no opaque fallback for older devices), while the *text*
  is replaced entirely with a `RedactedTextPlaceholder` (two gray bars) rather than
  relying on blur — `Modifier.blur()` was found to not reliably blur text glyphs on
  every device/GPU, so text-hiding had to be made bulletproof a different way.
- **Reveal state**: `rememberSaveable(post.postId) { mutableStateOf(false) }` — each
  card's "has this been revealed" flag is independent and keyed to that specific
  post, so scrolling away and back doesn't reset it.
- **Like/Comment/Share row**: `Like` toggles a local `remember { mutableStateOf }`
  for a filled/outline heart icon — purely visual, nothing persisted or sent
  anywhere. Comment and Share are no-op `IconButton`s (icons only, explicitly not
  wired to any feature).

### 6.3 `AnalysisScreen.kt` + `AnalysisViewModel.kt`

Loads **two** independent things in parallel using `async`/`await`:

```kotlin
val moodDeferred = async { repository.getMood(userId) }
val attentionDeferred = async { repository.getAttention(userId) }
val moodResult = moodDeferred.await()
val attentionResult = attentionDeferred.await()
```

Renders two cards in a scrollable `Column`:

- **`MoodCard`** — emoji + mood label + session length, three stat boxes
  (Confidence/Valence/Arousal), a trend row, and a hand-drawn `Canvas` line chart of
  valence over the session (no charting library — just `drawLine`/`drawCircle` calls
  computed from normalized data points).
- **`AttentionCard`** — reels-per-minute headline stat, avg/session stat boxes, a
  trend row, and a `Canvas` **bar chart** of per-minute scroll counts. Its trend
  color mapping is **intentionally inverted** from Mood's: for attention, a rising
  scroll rate is bad (red, "doomscrolling"), a falling rate is good (green) — the
  opposite of mood's "rising is good" mapping — because they measure conceptually
  opposite things.

Both charts are built the same way: normalize the data range to the Canvas's pixel
size, then issue raw draw calls inside a `Canvas(modifier) { ... }` block's
`DrawScope`.

### 6.4 `SettingsScreen.kt` + `SettingsStore.kt`

No ViewModel — reads/writes `SettingsStore` (a persisted singleton, see §7)
directly. Three pieces of UI, each backed by one thing in `SettingsStore`:
1. A text field + "Add" button, and a list of removable rows, for `blockedTerms`.
2. A `Slider` (0f..1f) for `blurThreshold`.
3. A `Slider` (0f..1f) for `similarityThreshold`.

Every change calls straight into `SettingsStore.addBlockedTerm()` /
`setBlurThreshold()` / etc., which updates the in-memory `mutableStateOf` (so the
UI updates immediately) **and** writes to `SharedPreferences` (so it survives app
restarts) in the same call.

### 6.5 `ProfileScreen.kt` + `ProfileViewModel.kt`

Shows `UserSession.displayName` (the name entered at login) and a list of
`PlatformToken`s from `MockAuthManager`, each with a Connect/Disconnect button that
calls `ProfileViewModel.toggleConnection(platform)`.

### 6.6 `BottomNavBar.kt` and `TopHeaderBar.kt`

Both are **pure, stateless components** — they take their current state and a
callback as parameters, and own no state of their own:

```kotlin
@Composable
fun BottomNavBar(selectedItem: NavigationContents, onItemSelected: (NavigationContents) -> Unit)
```

This is what makes them independently previewable (each has its own `@Preview`
with local `remember { mutableStateOf(...) }` standing in for the real navigation
state) and reusable regardless of *how* navigation is wired above them.
`NavigationContents` is the enum defining the three tabs (`HOME`, `ANALYSIS`,
`SETTINGS`), each carrying its icon, label, and route string together.

### 6.7 Compose Previews — testing UI without running the app

Every screen file ends with one or more `@Preview` composables. These render
directly inside Android Studio, with **no emulator, no device, no backend, and no
network** required. The trick for screens that need a `BackendApi`: each preview
constructs a small private class implementing `BackendApi` with hardcoded fake
responses (see `PreviewBackendApi` in `FeedScreen.kt`), then builds a real
ViewModel/Repository around that fake instead of the real
`RetrofitClient.backendApi`. This is why, throughout this project's development,
UI could be verified working *before* the backend or ML API were even reachable.

---

## 7. Local persistence — two small `SharedPreferences`-backed singletons

Neither of these uses a database or DataStore — just Android's simplest built-in
key-value store, appropriate given the small amount of data involved.

### `UserSession.kt`

```kotlin
object UserSession {
    var displayName by mutableStateOf("Guest")
        private set
    var isLoggedIn by mutableStateOf(false)
        private set

    fun restore(context: Context) { /* read from SharedPreferences */ }
    fun login(context: Context, name: String) { /* write + update state */ }
}
```

Backs the one-time login flow (§5.2) and Profile's displayed name.

### `SettingsStore.kt`

Same shape, three values instead of two (`blockedTerms: List<String>`,
`blurThreshold: Float`, `similarityThreshold: Float`). The list of blocked terms is
serialized to a single JSON string (`org.json.JSONArray`) for storage, since
`SharedPreferences` only stores primitives and `Set<String>` natively (and a `Set`
doesn't reliably preserve insertion order).

Both objects expose their state via `private set` — external code can *read*
`UserSession.displayName` freely, but can only *change* it by calling `login()`,
which guarantees the in-memory state and the persisted disk state never go out of
sync with each other.

---

## 8. Auth — entirely mocked, by design

There is **no real authentication** anywhere in this app. `MockAuthManager` and
`TokenStore` exist specifically to let the rest of the app (Login screen, Profile
screen) be built against a stable interface without an actual OAuth flow:

```kotlin
object MockAuthManager {
    fun isConnected(platform: String): Boolean
    fun connect(platform: String)     // just flips a local flag
    fun disconnect(platform: String)
    fun connectedPlatforms(): List<PlatformToken>
}
```

`TokenStore` holds a `PlatformToken(platform: String, connected: Boolean)` for
`"reddit"` and `"instagram"` in memory. Tapping "Connect" anywhere in the app calls
`connect()`, which does nothing more than set that boolean — no network call, no
token, no real Reddit/Instagram integration. This is intentional and documented in
`ARCHITECTURE.md`'s "Mocked login" section: it exists purely so the *UI* for a
connected-accounts feature could be built and demoed without the much larger effort
of implementing real OAuth for two platforms during a hackathon.

The one genuinely real piece of "identity" in the app is `UserSession.displayName`
— just a string the user typed at login, with zero verification.

---

## 9. Data models — the shapes moving through the app

| Kotlin class | Where it comes from | Purpose |
|---|---|---|
| `Post` | Client-constructed | `{id, text}` — used only by the `classify()` arbitrary-text path |
| `ClassificationResult` | `POST /classify` response | `{postId, shouldBlur, tags, wellbeingScore, reason}` |
| `FeedPost` | `POST /feed` response | Content **and** classification together — `{postId, text, imageUrl, shouldBlur, tags, wellbeingScore, reason}` |
| `MoodResponse` / `MoodHistoryPoint` | `GET /session/mood/{id}` | Mood card's entire data source |
| `AttentionResponse` / `AttentionMinutePoint` | `GET /session/attention/{id}` | Attention card's entire data source |
| `PlatformToken` | Local (`MockAuthManager`) | `{platform, connected}` — never touches the network |

Every network-facing model uses `@SerializedName` to bridge Kotlin's camelCase to
the backend's snake_case JSON keys. Fields that can legitimately be absent
(`wellbeingScore`, `reason`, `imageUrl`) are typed nullable (`Double?`, `String?`)
rather than given fake sentinel values.

`FeedPost` is the one model that changed shape mid-project: it used to be two
separate objects (`Post` + `ClassificationResult`) that `FeedScreen` had to
manually match up by ID. Once the backend started returning content and
classification together in one `/feed` response, `FeedPost` was introduced to
match that reality, and `PostCard` was simplified to take one object instead of
two — a direct example of the Kotlin model shape following the actual API
contract, not the other way around.

---

## 10. Image loading — Coil

`coil-compose` provides the `AsyncImage` composable used in `PostCard`:

```kotlin
AsyncImage(
    model = post.imageUrl,
    contentDescription = null,
    contentScale = ContentScale.Crop,
    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)),
)
```

Coil handles the network fetch, bitmap decoding, and both memory and disk caching
internally — nothing else in the app needs to know or care how the image actually
gets from a URL to pixels. It requires the `INTERNET` permission (declared in
`AndroidManifest.xml`) and, since dataset image URLs are plain `https://`, works
fine under the app's cleartext-HTTP policy (see §11) without any special
configuration.

---

## 11. Networking quirks specific to this app (worth knowing)

- **Cleartext HTTP**: the backend runs on a plain `http://` LAN address (no TLS
  certificate for a local dev server), but Android blocks cleartext traffic by
  default since API 28. `app/src/debug/AndroidManifest.xml` sets
  `android:usesCleartextTraffic="true"` — scoped to **debug builds only** via
  Android's source-set manifest merging, so release builds stay locked to HTTPS.
- **`INTERNET` permission**: declared in the main `AndroidManifest.xml` — required
  for any network access at all, regardless of HTTP vs HTTPS.
- **`RetrofitClient.BASE_URL`** is a hardcoded LAN IP that has to be updated by hand
  whenever the backend's host machine's network address changes — there is
  currently no build-time or runtime configuration for this (a known, accepted
  limitation for a hackathon project, discussed at length in `HANDOFF.md`).

---

## 12. Where to look next

- **`ARCHITECTURE.md`** — the backend pipeline (`content_blocking.py` →
  `scoring.py` → `feed_logic.py` → `mood_predictor.py` → `attention_tracker.py`),
  why steps run in that order, and the fallback browser demo.
- **`HANDOFF.md`** — current session-to-session state: what works, what's broken,
  what to do next, and a running list of build gotchas already hit and fixed.
- **`gradle/libs.versions.toml`** — every dependency and its exact pinned version.
