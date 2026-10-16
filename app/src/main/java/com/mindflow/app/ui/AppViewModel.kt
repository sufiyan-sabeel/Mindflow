package com.mindflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.MindflowApp
import com.mindflow.app.data.Achievement
import com.mindflow.app.data.AppRepository
import com.mindflow.app.data.AppState
import com.mindflow.app.data.GameResult
import com.mindflow.app.data.LevelProgress
import com.mindflow.app.data.PlayerProfile
import com.mindflow.app.data.RatingPoint
import com.mindflow.app.data.UserPreferences
import com.mindflow.app.data.remote.CloudSnapshot
import com.mindflow.app.data.remote.CloudRepository
import com.mindflow.app.data.remote.LeaderboardRow
import com.mindflow.app.domain.Achievements
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.domain.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Observable state of the online layer. Offline play never depends on this. */
data class CloudUiState(
    val configured: Boolean = false,
    val signedIn: Boolean = false,
    val isGuest: Boolean = false,
    val email: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val lastSyncAt: Long? = null,
    val pendingUploads: Int = 0
)

class AppViewModel(private val repo: AppRepository) : ViewModel() {

    private val cloud = CloudRepository()

    val state: StateFlow<AppState> =
        repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    private val _lastUnlocked = MutableStateFlow<Achievement?>(null)
    val lastUnlocked: StateFlow<Achievement?> = _lastUnlocked.asStateFlow()

    private val _cloud = MutableStateFlow(CloudUiState(configured = cloud.isConfigured))
    val cloudState: StateFlow<CloudUiState> = _cloud.asStateFlow()

    private val _leaderboard = MutableStateFlow<List<LeaderboardRow>?>(null)
    val leaderboard: StateFlow<List<LeaderboardRow>?> = _leaderboard.asStateFlow()

    private val _room = MutableStateFlow<com.mindflow.app.data.remote.RoomState?>(null)
    val room: StateFlow<com.mindflow.app.data.remote.RoomState?> = _room.asStateFlow()

    private val _openRooms = MutableStateFlow<List<com.mindflow.app.data.remote.RoomRow>>(emptyList())
    val openRooms: StateFlow<List<com.mindflow.app.data.remote.RoomRow>> = _openRooms.asStateFlow()

    private val _matches = MutableStateFlow<List<com.mindflow.app.data.remote.MatchRow>>(emptyList())
    val matches: StateFlow<List<com.mindflow.app.data.remote.MatchRow>> = _matches.asStateFlow()

    private var roomPollJob: kotlinx.coroutines.Job? = null

    init {
        refreshCloudIdentity()
        // Push anything that accumulated offline, then restore from the cloud.
        if (cloud.isSignedIn()) viewModelScope.launch { syncNow(silent = true) }
    }

    fun consumeUnlocked() { _lastUnlocked.value = null }

    // ------------------------------------------------------------- local edits

    fun completeOnboarding() = viewModelScope.launch {
        repo.update { it.copy(onboarded = true) }
    }

    fun setDisplayName(name: String) = viewModelScope.launch {
        val clean = name.trim().ifBlank { "Guest Player" }.take(24)
        repo.update { it.copy(profile = it.profile.copy(displayName = clean)) }
        if (cloud.isSignedIn()) {
            cloud.upsertProfile(clean, state.value.profile.avatarSeed, null)
        }
    }

    fun setAvatarSeed(seed: Int) = viewModelScope.launch {
        repo.update { it.copy(profile = it.profile.copy(avatarSeed = seed)) }
        if (cloud.isSignedIn()) {
            cloud.upsertProfile(state.value.profile.displayName, seed, null)
        }
    }

    fun updatePrefs(prefs: UserPreferences) = viewModelScope.launch {
        repo.update { it.copy(prefs = prefs) }
    }

    /** Community/college tag lives server-side only; it drives the community leaderboard tab. */
    fun setCommunity(community: String) = viewModelScope.launch {
        if (!cloud.isSignedIn()) {
            _cloud.value = _cloud.value.copy(message = "Sign in to set a community tag.")
            return@launch
        }
        val ok = cloud.upsertProfile(
            state.value.profile.displayName,
            state.value.profile.avatarSeed,
            community.trim().ifBlank { null }
        )
        _cloud.value = _cloud.value.copy(message = if (ok) "Community saved" else "Could not save community")
    }

    fun resetAll() = viewModelScope.launch { repo.reset() }

    // ------------------------------------------------------------------ cloud

    private fun refreshCloudIdentity() {
        _cloud.value = _cloud.value.copy(
            configured = cloud.isConfigured,
            signedIn = cloud.isSignedIn(),
            isGuest = cloud.isGuest(),
            email = cloud.currentEmail(),
            pendingUploads = state.value.results.count { !it.synced }
        )
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _cloud.value = _cloud.value.copy(busy = true, message = null)
        val res = cloud.signIn(email, password)
        _cloud.value = _cloud.value.copy(busy = false)
        res.fold(
            onSuccess = { _cloud.value = _cloud.value.copy(message = "Signed in"); syncNow() },
            onFailure = { _cloud.value = _cloud.value.copy(message = it.message ?: "Sign-in failed") }
        )
        refreshCloudIdentity()
    }

    fun signUp(email: String, password: String, displayName: String) = viewModelScope.launch {
        _cloud.value = _cloud.value.copy(busy = true, message = null)
        val res = cloud.signUp(email, password, displayName)
        _cloud.value = _cloud.value.copy(busy = false)
        res.fold(
            onSuccess = {
                _cloud.value = _cloud.value.copy(
                    message = "Account created. Check your email if confirmation is enabled."
                )
                refreshCloudIdentity()
                if (cloud.isSignedIn()) syncNow()
            },
            onFailure = { _cloud.value = _cloud.value.copy(message = it.message ?: "Sign-up failed") }
        )
    }

    fun continueAsGuest() = viewModelScope.launch {
        _cloud.value = _cloud.value.copy(busy = true, message = null)
        val res = cloud.signInAsGuest()
        _cloud.value = _cloud.value.copy(busy = false)
        res.fold(
            onSuccess = { syncNow() },
            onFailure = {
                _cloud.value = _cloud.value.copy(
                    message = "Guest sign-in is disabled on this project. Enable anonymous sign-ins in Supabase, or use email."
                )
            }
        )
        refreshCloudIdentity()
    }

    fun signOutUser() = viewModelScope.launch {
        cloud.signOut()
        _cloud.value = _cloud.value.copy(message = "Signed out")
        _leaderboard.value = null
        refreshCloudIdentity()
    }

    /**
     * Push every locally-recorded result that the server has not acknowledged, claim
     * today's daily challenges, then pull the server snapshot and merge it in.
     * Safe to call repeatedly: the server de-duplicates by client id.
     */
    fun syncNow(silent: Boolean = false) = viewModelScope.launch {
        if (!cloud.isConfigured) {
            if (!silent) _cloud.value = _cloud.value.copy(message = "Supabase is not configured in this build.")
            return@launch
        }
        if (!cloud.isSignedIn()) {
            if (!silent) _cloud.value = _cloud.value.copy(message = "Sign in to sync.")
            return@launch
        }
        _cloud.value = _cloud.value.copy(busy = true, message = null)

        val snapshot = state.value
        val today = Time.epochDay()

        cloud.ensureDaily(today)

        val pushed = mutableSetOf<String>()
        for (r in snapshot.results.filter { !it.synced && it.clientId.isNotBlank() }) {
            val outcome = cloud.submitResult(
                clientId = r.clientId,
                mode = r.mode.name,
                levelId = r.levelId,
                correct = r.correct,
                total = r.total,
                bestStreak = r.bestStreak,
                avgResponseMs = r.avgResponseMs
            )
            if (outcome != null) pushed.add(r.clientId)
        }

        for ((dayKey, ids) in snapshot.dailyCompletions) {
            val day = dayKeyToEpochDay(dayKey) ?: continue
            for (id in ids) cloud.claimDaily(day, id, 0)
        }

        if (pushed.isNotEmpty()) {
            repo.update { s ->
                s.copy(results = s.results.map { if (it.clientId in pushed) it.copy(synced = true) else it })
            }
        }

        val remote = cloud.pullSnapshot()
        if (remote != null) {
            repo.update { local -> mergeSnapshot(local, remote) }
            _cloud.value = _cloud.value.copy(message = "Synced")
        } else if (!silent) {
            _cloud.value = _cloud.value.copy(message = "Could not reach the server.")
        }

        _cloud.value = _cloud.value.copy(
            busy = false,
            lastSyncAt = System.currentTimeMillis(),
            pendingUploads = state.value.results.count { !it.synced }
        )
        refreshCloudIdentity()
    }

    fun loadLeaderboard(scope: String, kind: String, mode: String, period: String) = viewModelScope.launch {
        if (!cloud.isSignedIn()) {
            _cloud.value = _cloud.value.copy(message = "Sign in to see live leaderboards.")
            return@launch
        }
        _cloud.value = _cloud.value.copy(busy = true)
        _leaderboard.value = cloud.leaderboard(scope, kind, mode, period, limit = 50)
        _cloud.value = _cloud.value.copy(busy = false)
    }

    // ---------------------------------------------------------------- results

    /**
     * Records a finished game: updates XP, streak, win/loss, the mode's Elo rating,
     * level progress and achievements — then, if signed in, uploads it.
     */
    fun recordResult(result: GameResult, dailyChallengeId: String? = null) = viewModelScope.launch {
        val stamped = if (result.clientId.isBlank()) result.copy(clientId = UUID.randomUUID().toString()) else result

        repo.update { s ->
            val profile = s.profile
            val performance = stamped.accuracy.coerceIn(0.0, 1.0)
            val newRating = RatingEngine.update(profile.ratings.get(stamped.mode.skill), performance)
            val ratings = profile.ratings.with(stamped.mode.skill, newRating)

            val today = Time.epochDay()
            val streakDays = when (profile.lastActiveEpochDay) {
                today -> profile.streakDays.coerceAtLeast(1)
                today - 1 -> profile.streakDays + 1
                else -> 1L
            }

            val updatedProfile = profile.copy(
                xp = profile.xp + stamped.xp,
                gamesPlayed = profile.gamesPlayed + 1,
                wins = profile.wins + if (stamped.accuracy >= 0.7) 1 else 0,
                losses = profile.losses + if (stamped.accuracy < 0.7) 1 else 0,
                bestStreak = maxOf(profile.bestStreak, stamped.bestStreak),
                lastActiveEpochDay = today,
                streakDays = streakDays.toInt(),
                ratings = ratings,
                ratingHistory = (profile.ratingHistory + RatingPoint(today, ratings.overall)).takeLast(60)
            )

            val newLevelProgress = stamped.levelId?.let { lid ->
                val prev = s.progressFor(lid)
                s.levelProgress + (lid.toString() to LevelProgress(
                    stars = maxOf(prev.stars, stamped.stars),
                    bestScore = maxOf(prev.bestScore, stamped.score),
                    completed = prev.completed || stamped.stars >= 1
                ))
            } ?: s.levelProgress

            val newDaily = dailyChallengeId?.let { id ->
                val key = Time.todayKey()
                val done = s.dailyCompletions[key].orEmpty()
                s.dailyCompletions + (key to (done + id).distinct())
            } ?: s.dailyCompletions

            var next = s.copy(
                profile = updatedProfile,
                results = (s.results + stamped).takeLast(300),
                levelProgress = newLevelProgress,
                dailyCompletions = newDaily
            )

            val unlocked = Achievements.newlyUnlocked(next)
            if (unlocked.isNotEmpty()) {
                next = next.copy(unlockedAchievements = next.unlockedAchievements + unlocked.map { it.id })
                _lastUnlocked.value = unlocked.first()
            }
            next
        }

        // Best-effort cloud upload; failures are retried by the next syncNow().
        if (cloud.isSignedIn()) {
            val ok = cloud.submitResult(
                clientId = stamped.clientId,
                mode = stamped.mode.name,
                levelId = stamped.levelId,
                correct = stamped.correct,
                total = stamped.total,
                bestStreak = stamped.bestStreak,
                avgResponseMs = stamped.avgResponseMs
            )
            if (ok != null) {
                repo.update { s ->
                    s.copy(results = s.results.map {
                        if (it.clientId == stamped.clientId) it.copy(synced = true) else it
                    })
                }
            }
            dailyChallengeId?.let { cloud.claimDaily(Time.epochDay(), it, stamped.score) }
            refreshCloudIdentity()
        }
    }

    // ------------------------------------------------------------------ merge

    /**
     * Merge server state into local state without ever losing local progress:
     * counters take the larger value, level stars/scores take the best, achievements
     * and daily completions are unioned, and ratings come from the server (the server
     * is the authority for competitive values).
     */
    private fun mergeSnapshot(local: AppState, remote: CloudSnapshot): AppState {
        val p = local.profile
        val mergedProfile: PlayerProfile = p.copy(
            displayName = if (p.displayName == "Guest Player" && !remote.displayName.isNullOrBlank())
                remote.displayName else p.displayName,
            avatarSeed = if (p.avatarSeed == 0) (remote.avatarSeed ?: 0) else p.avatarSeed,
            xp = maxOf(p.xp, remote.xp),
            gamesPlayed = maxOf(p.gamesPlayed, remote.gamesPlayed),
            wins = maxOf(p.wins, remote.wins),
            losses = maxOf(p.losses, remote.losses),
            bestStreak = maxOf(p.bestStreak, remote.bestStreak),
            streakDays = maxOf(p.streakDays, remote.streakDays),
            ratings = p.ratings.copy(
                math = remote.ratings["math"] ?: p.ratings.math,
                logic = remote.ratings["logic"] ?: p.ratings.logic,
                memory = remote.ratings["memory"] ?: p.ratings.memory,
                puzzle = remote.ratings["puzzle"] ?: p.ratings.puzzle
            )
        )

        val mergedLevels = local.levelProgress.toMutableMap()
        for (lvl in remote.levels) {
            val prev = mergedLevels[lvl.levelId.toString()] ?: LevelProgress()
            mergedLevels[lvl.levelId.toString()] = LevelProgress(
                stars = maxOf(prev.stars, lvl.stars),
                bestScore = maxOf(prev.bestScore, lvl.bestScore),
                completed = prev.completed || lvl.completed
            )
        }

        val mergedDaily = local.dailyCompletions.toMutableMap()
        for (d in remote.daily) {
            val key = Time.dateKey(d.epochDay)
            mergedDaily[key] = (mergedDaily[key].orEmpty() + d.challengeId).distinct()
        }

        return local.copy(
            profile = mergedProfile,
            levelProgress = mergedLevels,
            dailyCompletions = mergedDaily,
            unlockedAchievements = (local.unlockedAchievements + remote.achievements).distinct()
        )
    }

    private fun dayKeyToEpochDay(key: String): Long? = runCatching {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(key)?.time?.div(86_400_000L)
    }.getOrNull()

    // ------------------------------------------------------------ multiplayer

    fun createRoom(mode: String) = viewModelScope.launch {
        if (!cloud.isSignedIn()) {
            _cloud.value = _cloud.value.copy(message = "Sign in to play online.")
            return@launch
        }
        _cloud.value = _cloud.value.copy(busy = true)
        val room = cloud.createRoom(mode)
        _cloud.value = _cloud.value.copy(busy = false)
        if (room == null) {
            _cloud.value = _cloud.value.copy(message = "Could not create a room.")
        } else {
            _room.value = cloud.roomState(room.id)
            startRoomPolling(room.id)
        }
    }

    fun joinRoom(code: String) = viewModelScope.launch {
        if (!cloud.isSignedIn()) {
            _cloud.value = _cloud.value.copy(message = "Sign in to play online.")
            return@launch
        }
        _cloud.value = _cloud.value.copy(busy = true)
        val roomId = cloud.joinRoom(code.trim().uppercase())
        _cloud.value = _cloud.value.copy(busy = false)
        if (roomId == null) {
            _cloud.value = _cloud.value.copy(message = "Room not found or full.")
        } else {
            _room.value = cloud.roomState(roomId)
            startRoomPolling(roomId)
        }
    }

    fun setRoomReady(ready: Boolean) = viewModelScope.launch {
        val id = _room.value?.id ?: return@launch
        cloud.setReady(id, ready)
        _room.value = cloud.roomState(id)
    }

    fun leaveRoom() = viewModelScope.launch {
        roomPollJob?.cancel()
        val id = _room.value?.id
        if (id != null) cloud.leaveRoom(id)
        _room.value = null
        refreshOpenRooms()
    }

    fun refreshOpenRooms(mode: String? = null) = viewModelScope.launch {
        if (!cloud.isSignedIn()) return@launch
        _openRooms.value = cloud.openRooms(mode) ?: emptyList()
    }

    fun refreshMatches() = viewModelScope.launch {
        if (!cloud.isSignedIn()) return@launch
        _matches.value = cloud.matchHistory(20) ?: emptyList()
    }

    fun submitDuelScore(roomId: String, correct: Int, total: Int, bestStreak: Int) = viewModelScope.launch {
        cloud.submitRoomScore(roomId, correct, total, bestStreak)
    }

    /** Light polling of room state; Realtime is enabled server-side but polling keeps this reliable. */
    private fun startRoomPolling(roomId: String) {
        roomPollJob?.cancel()
        roomPollJob = viewModelScope.launch {
            while (true) {
                val s = cloud.roomState(roomId)
                if (s != null) _room.value = s
                if (s?.status == "finished") { refreshMatches(); break }
                kotlinx.coroutines.delay(2500)
            }
        }
    }

    override fun onCleared() {
        roomPollJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(app: MindflowApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(app.repository) }
        }
    }
}
