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
| `Mindflow-1.0.0-release.apk` | Signed release build | ~6.6 MB |
| `Mindflow-1.0.0-debug.apk` | Debug build | ~9.5 MB |

Install on a device with **Android 7.0 (API 24) or newer**. Enable "Install unknown apps" for your
file manager / browser if prompted.

---

## What actually works

This is a functioning app, not a mock-up. Everything below is implemented and playable offline:

- **5 playable game modes**, each with its own generator, validation and scoring:
  - **Math Sprint** — timed arithmetic (+, −, ×, ÷), numeric keypad, streak tracking.
  - **Pattern Logic** — arithmetic/geometric sequence completion (multiple choice).
  - **Memory Challenge** — memorise a digit sequence, then reproduce it.
  - **Logic Grid** — deduction questions (digit sums, largest value, squares sequences).
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

## What is intentionally NOT enabled

The original spec asked for online accounts, cloud sync and real-time multiplayer. Those require an
external backend and are **not** faked here:

- **Online duels / matchmaking / private rooms** — shown as "Unavailable — backend not configured".
  A clearly-labelled *offline practice opponent* is offered instead. No bots are ever presented as
  live players.
- **Firebase auth, Cloud Firestore, cloud leaderboards** — not wired up. The app never claims a live
  global ranking; the leaderboard is local + labelled sample data.
- **Google sign-in** — not configured.

To add these, provide a backend (e.g. Firebase) and implement the repository interfaces; the UI is
already structured to degrade gracefully when the network is unavailable.

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
    domain/
      Engine.kt                 Level engine, Elo rating, daily challenges, achievements, time
    game/
      GameContent.kt            Deterministic, testable question/sequence generators
    ui/
      AppViewModel.kt           State holder; records results, updates ratings/XP/achievements
      Nav.kt                    Navigation graph and routes
      theme/Theme.kt            Mindflow design system (dark-first palette + typography)
      components/Common.kt      Reusable composables (cards, buttons, keypad, stat tiles)
      screens/
        ScreensHome.kt          Splash, onboarding, Arena/Puzzles/Compete/Feed/More
        ScreensPlay.kt          Gameplay for all five modes + results overlay
        ScreensMisc.kt          Leaderboard, profile, settings, daily challenges
  src/test/java/com/mindflow/app/
    EngineTest.kt               10 unit tests for generators, ratings, levels, daily seeding
```

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
