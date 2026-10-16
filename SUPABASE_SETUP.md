# Supabase setup for Mindflow

Mindflow is offline-first: **every single-player feature works with no backend at all.**
This document is only needed to switch on the online layer — accounts, cloud sync,
real leaderboards and online duels.

Nothing here requires a paid service. The whole backend runs on the Supabase free plan.

---

## 1. What the backend provides

| Feature | Table(s) / function |
|---|---|
| Accounts (email, guest) | Supabase Auth (`auth.users`) |
| Profiles | `profiles` (auto-created by trigger) |
| Progression (XP, streak, W/L) | `player_progress` |
| Campaign levels, stars | `level_progress` |
| Match/game results | `game_results` (append-only, idempotent) |
| Skill ratings | `player_ratings` |
| Achievements | `achievements`, `player_achievements` |
| Daily challenges | `daily_challenges`, `daily_challenge_results` |
| Friends | `friendships` |
| Duels | `rooms`, `room_players`, `match_history` |
| Leaderboards | `leaderboard_scores` + `get_leaderboard()` |

All competitive writes go through `SECURITY DEFINER` functions that recompute XP,
stars, score, rating and achievements server-side. The client cannot write those
tables directly — the tables have Row Level Security enabled with **no insert/update
policy** for them.

---

## 2. Create the project

1. Sign in at <https://supabase.com> and create a project (free plan).
2. Pick the region closest to your players (e.g. `ap-south-1` / Mumbai for India).
3. Save the database password somewhere safe — you will **not** need it in the app.

> A project was already created for this repository: ref `vrizaxewdnxkvlyfanpt`
> (region `ap-south-1`). You can reuse it, or create your own and apply the same
> migrations.

## 3. Apply the migrations

The SQL lives in `supabase/migrations/` and must be applied **in order**:

```
0001_schema.sql        tables, indexes, constraints, new-user trigger
0002_rls.sql           Row Level Security policies for every table
0003_functions.sql     submit_game_result, claim_daily_challenge, get_my_snapshot, get_leaderboard
0004_multiplayer.sql   rooms, room_players, match_history, finish_match + Realtime publication
0005_seed.sql          achievement catalogue, ensure_daily_challenges
0006_hardening.sql     pinned search_path, anon EXECUTE revoked
0007_queries.sql       upsert_profile, get_room_state, list_open_rooms, get_match_history
0008_fix_room_code.sql room-code generation fix
```

Three ways to apply them, pick one:

**a) Supabase CLI** (recommended)
```bash
supabase link --project-ref <your-project-ref>
supabase db push
```

**b) SQL editor** — paste each file into the dashboard SQL editor, in order.

**c) Any Postgres client** — connect with the connection string and run the files in order.

Afterwards run the dashboard **Advisors** (Database → Advisors) and confirm there are no
`rls_disabled` findings. One warning remains by design:
`authenticated_security_definer_function_executable` — signed-in users are *meant* to call
the RPC surface (`submit_game_result`, `get_leaderboard`, …). The `anon` variants are revoked.

## 4. Configure authentication

Dashboard → Authentication:

- **Email provider**: on by default. Decide whether to require email confirmation.
  - If confirmation is **on**, a new sign-up returns no session until the user clicks the link.
    The app handles this and tells the user to check their email.
  - If confirmation is **off**, sign-up signs the user straight in.
- **Anonymous sign-ins**: enable this if you want the "Continue as guest" button to work.
  Guest progress syncs but is tied to the device until the user links an email.
- **Site URL / redirect URLs**: set to your app's scheme if you later add OAuth.
- **Google sign-in**: optional. Enable the Google provider and add the client id/secret in
  the dashboard; the app has no Google button yet, so this is a follow-up.

## 5. Get the client configuration

Dashboard → Project Settings → API:

- **Project URL** → `https://<ref>.supabase.co`
- **Publishable key** (`sb_publishable_...`) or the legacy **anon** key.

Both are *publishable*: they are designed to be shipped in client apps and are protected
by RLS. Use only these.

> **Never** put the `service_role` key, the database password, or any other privileged
> secret in the Android app, in this repository, or in build properties committed to git.

## 6. Build with the configuration

```bash
./gradlew assembleDebug \
  -Pmindflow.supabaseUrl="https://<ref>.supabase.co" \
  -Pmindflow.supabaseAnonKey="sb_publishable_..."
```

Without those flags the build still succeeds; the app simply reports that online features
are not configured and stays fully playable offline.

## 7. CI secrets (GitHub Actions)

`.github/workflows/android.yml` builds and tests on every push. Add these repository
secrets (Settings → Secrets and variables → Actions):

| Secret | Purpose |
|---|---|
| `MINDFLOW_SUPABASE_URL` | project URL |
| `MINDFLOW_SUPABASE_ANON_KEY` | publishable key |
| `MINDFLOW_KEYSTORE_BASE64` | `base64 -w0 release.jks` — optional, enables the signed release build |
| `MINDFLOW_STORE_PASSWORD` | keystore password |
| `MINDFLOW_KEY_ALIAS` | key alias |
| `MINDFLOW_KEY_PASSWORD` | key password |

If the keystore secrets are absent the workflow only builds the debug APK.

---

## 8. Free-plan limits and caveats

Supabase changes plan limits over time — check <https://supabase.com/pricing> before
relying on a number. The operationally important ones:

- **Projects pause after about a week of inactivity** on the free plan. A paused project
  returns errors until you restore it from the dashboard. This is the most likely cause of
  "the app suddenly can't sync" after a quiet period.
- The free plan allows a small number of **active projects** (historically 2). Pausing one
  frees a slot.
- Database size, egress and monthly active users are capped; a game like this uses very
  little — a few rows per game, and small JSON snapshots.
- **Realtime** has a concurrent-connection cap on the free tier. The app deliberately uses
  short-interval polling for the room lobby instead of holding a websocket open, so a duel
  does not consume a long-lived Realtime connection. Realtime is enabled on `rooms`,
  `room_players` and `match_history` if you want to switch the lobby to push updates.
- No credit card is required, and nothing in this project requires a paid add-on.

---

## 9. Status of each online feature

**Implemented and verified against the live project:**

- Email/password sign-up and sign-in, guest (anonymous) sign-in, sign-out.
- Profile auto-provisioning on sign-up (profile + progress + four ratings rows).
- Cloud sync: unsynced local results are pushed, daily challenges claimed, and the server
  snapshot is merged back (counters take the max, stars take the best, achievements union).
- Server-side validation: XP, stars, score, rating and achievements are recomputed on the
  server; the client's values for those fields are ignored.
- Idempotency: re-sending the same result is a no-op (verified — a duplicate submit left
  one row, not two).
- Row Level Security: direct client writes to `game_results` are rejected (verified).
- Leaderboards: `get_leaderboard` supports global / community / friends scopes and
  score / rating / best-time kinds, weekly or all-time. Only real registered players appear.
- Duels: create room → join by code → ready → both submit → server computes scores, decides
  the winner, records the match and applies Elo to both players (verified end-to-end).

**Requires your configuration before it will run:**

- Any of the above, until you create the project, apply the migrations, and build with
  `-Pmindflow.supabaseUrl` / `-Pmindflow.supabaseAnonKey`.
- Guest sign-in also needs **Anonymous sign-ins** enabled in the dashboard.

**Not implemented (deliberately):**

- Google / Apple OAuth buttons (provider plumbing exists server-side; no UI yet).
- Friend requests UI — the `friendships` table and its RLS policies exist, but there is no
  search/add/accept screen yet.
- Realtime push for the duel lobby — the app polls; Realtime is enabled but not wired.
- Matchmaking queue — the app lists open rooms instead; a queue is a follow-up.
- Server-side rate limiting beyond the payload sanity checks in `submit_game_result`.
