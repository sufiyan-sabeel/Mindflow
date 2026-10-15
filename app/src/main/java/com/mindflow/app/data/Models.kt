package com.mindflow.app.data

import kotlinx.serialization.Serializable

/** Skill domains that carry their own Elo-style rating. */
enum class Skill { MATH, LOGIC, MEMORY, PUZZLE }

/** Every playable game mode. Kept as a closed enum so the level engine can target them. */
enum class GameMode(val title: String, val skill: Skill) {
    MATH_SPRINT("Math Sprint", Skill.MATH),
    CROSS_MATH("Cross-Math", Skill.MATH),
    MAZE("Math Maze", Skill.LOGIC),
    PATTERN("Pattern Logic", Skill.LOGIC),
    MEMORY("Memory Challenge", Skill.MEMORY),
    LOGIC("Logic Lab", Skill.LOGIC),
    CLASSIC("Classic Puzzles", Skill.PUZZLE),
    REFLEX("Speed Reflex", Skill.PUZZLE);

    companion object {
        fun fromId(id: String): GameMode = entries.firstOrNull { it.name == id } ?: MATH_SPRINT
    }
}

@Serializable
data class Ratings(
    val math: Int = 1000,
    val logic: Int = 1000,
    val memory: Int = 1000,
    val puzzle: Int = 1000
) {
    val overall: Int get() = ((math + logic + memory + puzzle) / 4.0).toInt()
    fun get(skill: Skill): Int = when (skill) {
        Skill.MATH -> math
        Skill.LOGIC -> logic
        Skill.MEMORY -> memory
        Skill.PUZZLE -> puzzle
    }
    fun with(skill: Skill, value: Int): Ratings = when (skill) {
        Skill.MATH -> copy(math = value)
        Skill.LOGIC -> copy(logic = value)
        Skill.MEMORY -> copy(memory = value)
        Skill.PUZZLE -> copy(puzzle = value)
    }
}

@Serializable
data class RatingPoint(val epochDay: Long, val overall: Int)

@Serializable
data class UserPreferences(
    val soundOn: Boolean = true,
    val hapticsOn: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false
)

@Serializable
data class GameResult(
    val mode: GameMode,
    val levelId: Int? = null,
    val correct: Int,
    val total: Int,
    val bestStreak: Int,
    val avgResponseMs: Long,
    val score: Int,
    val stars: Int,
    val xp: Int,
    val epochDay: Long,
    val timestamp: Long
) {
    val accuracy: Double get() = if (total == 0) 0.0 else correct.toDouble() / total
}

@Serializable
data class LevelProgress(
    val stars: Int = 0,
    val bestScore: Int = 0,
    val completed: Boolean = false
)

@Serializable
data class PlayerProfile(
    val displayName: String = "Guest Player",
    val avatarSeed: Int = 0,
    val xp: Int = 0,
    val gamesPlayed: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val bestStreak: Int = 0,
    val friendsCount: Int = 0,
    val streakDays: Int = 0,
    val lastActiveEpochDay: Long = -1L,
    val ratings: Ratings = Ratings(),
    val ratingHistory: List<RatingPoint> = emptyList()
) {
    val playerLevel: Int get() = 1 + xp / 500
    val xpIntoLevel: Int get() = xp % 500
}

@Serializable
data class AppState(
    val onboarded: Boolean = false,
    val profile: PlayerProfile = PlayerProfile(),
    val results: List<GameResult> = emptyList(),
    /** levelId (as String) -> progress */
    val levelProgress: Map<String, LevelProgress> = emptyMap(),
    val unlockedAchievements: List<String> = emptyList(),
    /** epochDay (as String) -> set of completed daily challenge ids */
    val dailyCompletions: Map<String, List<String>> = emptyMap(),
    val prefs: UserPreferences = UserPreferences()
) {
    fun progressFor(levelId: Int): LevelProgress = levelProgress[levelId.toString()] ?: LevelProgress()
    val highestUnlockedLevel: Int
        get() {
            var h = 1
            while (levelProgress[(h).toString()]?.completed == true) h++
            return h
        }
}
