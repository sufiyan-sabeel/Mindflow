# Mindflow — Android Brain Training Game

Mindflow is an offline-first brain-training game for Android: fast arithmetic sprints, pattern and
logic puzzles, memory challenges and a reflex game, wrapped in a level campaign, XP, stars,
Elo-style ratings and a profile with statistics.

Built with **Kotlin + Jetpack Compose (Material 3)**, a single Gradle module, and local
persistence. It installs and runs without any account or network connection.

---

## Download

Pre-built APKs are attached to the [Releases](../../releases) page:

| File | Type | Size |
|------|------|------|
| `Mindflow-1.2.0-release.apk` | Signed release build | ~8.6 MB |
| `Mindflow-1.2.0-debug.apk` | Debug build | ~13 MB |

Install on a device with **Android 7.0 (API 24) or newer**. Enable "Install unknown apps" for your
file manager / browser if prompted.

---

## What actually works

This is a functioning app, not a mock-up. Everything below is implemented and playable offline:

- **8 playable game modes**, each with its own generator, answer validation and scoring:
  - **Math Sprint** — timed arithmetic (+, −, ×, ÷) plus percentages and fractions; numeric keypad, streak tracking.
  - **Cross-Math** — a 3×3 grid of numbers and operators; every row and column must evaluate to its target. Undo/reset/check.
  - **Math Maze** — traverse a grid top-left to bottom-right (right/down only) collecting numbers to hit an exact target, avoiding blocked cells.
  - **Memory Challenge** — memorise a digit sequence, then reproduce it.
  - **Logic Lab** — deduction questions (digit sums, largest value, squares sequences).
  - **Pattern Logic** — arithmetic/geometric sequence completion (multiple choice).
  - **Classic Puzzles** — mini-Sudoku (4×4 and 6×6) with unique-solution generation.
  - **Speed Reflex** — reaction-time rounds with early-tap detection.
- **100-level campaign** across five chapters (Foundations, Focus, Calculation, Logic Lab,
  Mastermind). Levels are generated from structured config — content is produced algorithmically,
  not hardcoded. Sequential unlocking, 1–3 stars per level, replay to improve.
- **Daily challenges** — five seeded challenges per day (math, logic, memory, speed, mixed) with
  deterministic seeds, completion tracking and a day-streak counter.
- **XP, player levels, achievements** (12) and best-streak records.
- **Elo-style ratings** per skill (Math, Logic, Memory, Puzzle) with tiers (Bronze → Master) and a
  rating-history chart. Ratings update only from completed, validated sessions.
- **Profile & statistics** — editable name, avatar, wins/losses, games played, rating breakdown,
  rating trend, recent match history.
- **Leaderboard** — Friends / Community / Global tabs. Shows your real rating alongside
  **clearly-labelled sample opponents** (preview data), never presented as a live leaderboard.
- **Settings** — sound, haptics, reduced-motion and high-contrast toggles; progress reset.
- **Offline persistence** — all progress is saved on-device and survives restarts.

## Online features (Supabase)

From 1.2.0 the app has a real backend: **Supabase** (free plan). See
[SUPABASE_SETUP.md](SUPABASE_SETUP.md) for the full setup. Summary:

- **Accounts** — email/password sign-up and sign-in, guest (anonymous) sign-in, sign-out.
- **Cloud sync** — unsynced results are pushed, daily challenges claimed, and the server snapshot
  merged back on a new device. Counters take the max, stars take the best, achievements union, so
  local progress is never lost.
- **Server-side validation** — XP, stars, score, rating and achievements are recomputed in Postgres.
  The client's values for those fields are ignored. Results are idempotent by client id, so a retry
  after a dropped connection cannot double-count.
- **Real leaderboards** — Friends / Community / Global, weekly or all-time, with only genuine
  registered players. Sample opponents remain **clearly labelled** and are only shown when signed out.
- **Online duels** — private rooms with 6-character codes, a lobby with ready state, and
  server-decided match results and Elo changes.
- **Security** — Row Level Security on every table, no direct client writes to competitive tables,
  and only the *publishable* key ships in the app. The service-role key is never embedded.

Without configuration the app is unchanged: fully playable offline, with every online surface
reporting itself as "not configured" rather than failing.

### Still not implemented

- Google/Apple OAuth buttons (provider plumbing exists; no UI).
- Friend requests UI — the `friendships` table and policies exist, but no search/add/accept screen.
- Realtime push for the duel lobby — Realtime is enabled on the tables, but the app polls instead.
- A matchmaking queue — the app lists open rooms.

---

## Architecture

```
app/
  src/main/java/com/mindflow/app/
    MindflowApp.kt              Application; owns the repository
    MainActivity.kt             Compose entry point
    data/
      Models.kt                 Serializable domain models (AppState, PlayerProfile, GameResult, ...)
      AppRepository.kt          DataStore-backed JSON persistence (single source of truth)
      Achievement.kt            Achievement definition type
      remote/
        SupabaseProvider.kt     Builds the Supabase client from BuildConfig (publishable key only)
        CloudRepository.kt      Auth + RPCs only; every call is best-effort so offline never breaks
        Dtos.kt                 Wire types for the RPC surface
    ../supabase/migrations/     Eight ordered SQL migrations (schema, RLS, functions, hardening)
    domain/
      Engine.kt                 Level engine, Elo rating, daily challenges, achievements, time
    game/
      GameContent.kt            Deterministic, testable question/sequence generators
      CrossMath.kt              Cross-Math generator + uniqueness solver + validation
      MathMaze.kt               Math Maze generator + path solver + validation
      MiniSudoku.kt             Mini-Sudoku generator + solution counter + validation
    ui/
      AppViewModel.kt           State holder; records results, updates ratings/XP/achievements
      Nav.kt                    Navigation graph and routes
      theme/Theme.kt            Mindflow design system (dark-first palette + typography)
      components/Common.kt      Reusable composables (cards, buttons, keypad, stat tiles)
      screens/
        ScreensHome.kt          Splash, onboarding, Arena/Puzzles/Compete/Feed/More
        ScreensPlay.kt          Gameplay for all eight modes + results overlay
        ScreensMisc.kt          Leaderboard (live or sample), profile, settings, daily challenges
        AccountScreen.kt        Sign in / sign up / guest, sync status, community tag
  src/test/java/com/mindflow/app/
    EngineTest.kt               10 tests: generators, ratings, levels, daily seeding
    GameEngineTest.kt           17 tests: Cross-Math uniqueness, Maze solvability, Sudoku uniqueness
```

Continuous integration lives in `.github/workflows/android.yml`: it runs the unit tests and builds
the APK on every push, and builds a signed release when keystore secrets are present.

### Game engine guarantees

Every puzzle is generated, not hand-authored, and each generator is verified by unit tests:

- **Cross-Math** starts from a solved grid, then removes givens one at a time, keeping the puzzle
  only while a backtracking solver still finds **exactly one** solution.
- **Math Maze** derives its target from a real right/down path and confirms reachability with a
  dynamic-programming path counter, so a solution always exists.
- **Mini-Sudoku** fills a valid Latin-square grid, then removes cells while a solution counter still
  reports **exactly one** solution.
- **Answer checking** is separate from generation: `CrossMath.isSolved`, `MathMaze.validatePath`
  (which rejects wrong moves, blocked cells and wrong sums with a specific reason) and
  `MiniSudoku.isSolved` all validate player input against the rules, not against a stored answer.

**Data flow:** UI → `AppViewModel` → `AppRepository` (DataStore) → `StateFlow<AppState>` → UI.
Game rules live in `domain/` and `game/` and are independent of Compose, so they are unit-testable.

### Persistence note

The spec suggested Room. This build uses **DataStore + kotlinx.serialization** for the whole
`AppState` — it is lighter, needs no annotation processor, and is fully sufficient for the data
volumes here. `AppRepository` exposes a narrow surface (`state` flow + `update`), so a Room-backed
implementation can replace it without touching the UI.

---

## Build from source

### Requirements
- JDK 17
- Android SDK with **platform 34** and **build-tools 34.0.0**
- Gradle 8.7 (a wrapper is included — use `./gradlew`)

Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`.

### Debug APK
```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

### Release APK
Release signing is driven by Gradle properties so no secret is committed. Create a keystore:

```bash
keytool -genkeypair -v -keystore mindflow-release.jks -alias mindflow \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then build:
```bash
./gradlew assembleRelease \
  -Pmindflow.storeFile=/absolute/path/mindflow-release.jks \
  -Pmindflow.storePassword=YOUR_PASSWORD \
  -Pmindflow.keyAlias=mindflow \
  -Pmindflow.keyPassword=YOUR_PASSWORD
# output: app/build/outputs/apk/release/app-release.apk
```

If no signing properties are supplied, the release build falls back to the debug key (fine for
testing, not for store distribution).

### Tests
```bash
./gradlew testDebugUnitTest
# report: app/build/reports/tests/testDebugUnitTest/index.html
```

---

## Tech stack

| Component | Choice |
|-----------|--------|
| Language | Kotlin 1.9.24 |
| UI | Jetpack Compose + Material 3 (BOM 2024.06.00) |
| Navigation | Navigation Compose 2.7.7 |
| Persistence | DataStore Preferences + kotlinx.serialization |
| Concurrency | Kotlin Coroutines / Flow |
| Build | Android Gradle Plugin 8.5.2, Gradle 8.7 |
| min / target / compile SDK | 24 / 34 / 34 |

## Design system

Dark-first palette: background `#101010`, surfaces `#1A1A1D` / `#242428`, primary purple
`#A995FF`, accent lime `#B7FF59`, text `#F5F5F5` / `#A3A3AA`, borders `#39393F`. Rounded cards, thin
borders, restrained accents.

## License

Provided as-is for the project owner. Original assets and algorithms; no proprietary content from
other apps is used.
