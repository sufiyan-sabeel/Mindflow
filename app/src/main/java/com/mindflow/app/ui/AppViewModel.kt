package com.mindflow.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mindflow.app.MindflowApp
import com.mindflow.app.data.Achievement
import com.mindflow.app.data.AppRepository
import com.mindflow.app.data.GameResult
import com.mindflow.app.data.LevelProgress
import com.mindflow.app.data.RatingPoint
import com.mindflow.app.data.UserPreferences
import com.mindflow.app.domain.Achievements
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.domain.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(private val repo: AppRepository) : ViewModel() {

    val state: StateFlow<com.mindflow.app.data.AppState> =
        repo.state.stateIn(viewModelScope, SharingStarted.Eagerly, com.mindflow.app.data.AppState())

    private val _lastUnlocked = MutableStateFlow<Achievement?>(null)
    val lastUnlocked: StateFlow<Achievement?> = _lastUnlocked.asStateFlow()

    fun consumeUnlocked() { _lastUnlocked.value = null }

    fun completeOnboarding() = viewModelScope.launch {
        repo.update { it.copy(onboarded = true) }
    }

    fun setDisplayName(name: String) = viewModelScope.launch {
        val clean = name.trim().ifBlank { "Guest Player" }.take(24)
        repo.update { it.copy(profile = it.profile.copy(displayName = clean)) }
    }

    fun setAvatarSeed(seed: Int) = viewModelScope.launch {
        repo.update { it.copy(profile = it.profile.copy(avatarSeed = seed)) }
    }

    fun updatePrefs(prefs: UserPreferences) = viewModelScope.launch {
        repo.update { it.copy(prefs = prefs) }
    }

    fun resetAll() = viewModelScope.launch { repo.reset() }

    /**
     * Records a finished game: updates XP, streak, win/loss, the mode's Elo rating, level progress
     * and any newly earned achievements. All derived from the single validated [GameResult].
     */
    fun recordResult(result: GameResult, dailyChallengeId: String? = null) = viewModelScope.launch {
        repo.update { s ->
            val profile = s.profile
            val performance = result.accuracy.coerceIn(0.0, 1.0)
            val newRating = RatingEngine.update(profile.ratings.get(result.mode.skill), performance)
            val ratings = profile.ratings.with(result.mode.skill, newRating)

            val today = Time.epochDay()
            val streakDays = when (profile.lastActiveEpochDay) {
                today -> profile.streakDays.coerceAtLeast(1)
                today - 1 -> profile.streakDays + 1
                else -> 1L
            }

            val updatedProfile = profile.copy(
                xp = profile.xp + result.xp,
                gamesPlayed = profile.gamesPlayed + 1,
                wins = profile.wins + if (result.accuracy >= 0.7) 1 else 0,
                losses = profile.losses + if (result.accuracy < 0.7) 1 else 0,
                bestStreak = maxOf(profile.bestStreak, result.bestStreak),
                lastActiveEpochDay = today,
                streakDays = streakDays.toInt(),
                ratings = ratings,
                ratingHistory = (profile.ratingHistory + RatingPoint(today, ratings.overall)).takeLast(60)
            )

            val newLevelProgress = result.levelId?.let { lid ->
                val prev = s.progressFor(lid)
                s.levelProgress + (lid.toString() to LevelProgress(
                    stars = maxOf(prev.stars, result.stars),
                    bestScore = maxOf(prev.bestScore, result.score),
                    completed = prev.completed || result.stars >= 1
                ))
            } ?: s.levelProgress

            val newDaily = dailyChallengeId?.let { id ->
                val key = Time.todayKey()
                val done = s.dailyCompletions[key].orEmpty()
                s.dailyCompletions + (key to (done + id).distinct())
            } ?: s.dailyCompletions

            var next = s.copy(
                profile = updatedProfile,
                results = (s.results + result).takeLast(300),
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
    }

    companion object {
        fun factory(app: MindflowApp): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(app.repository) }
        }
    }
}
