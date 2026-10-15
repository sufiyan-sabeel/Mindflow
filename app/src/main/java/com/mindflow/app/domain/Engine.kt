package com.mindflow.app.domain

import com.mindflow.app.data.Achievement
import com.mindflow.app.data.AppState
import com.mindflow.app.data.GameMode
import com.mindflow.app.data.Skill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Time helpers. A single epoch-day definition keeps daily seeds deterministic per calendar day. */
object Time {
    fun epochDay(): Long = System.currentTimeMillis() / 86_400_000L

    fun dateKey(day: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(day * 86_400_000L))
    }

    fun todayKey(): String = dateKey(epochDay())
}

/** A campaign level. Content is generated from this config at play time — nothing is hardcoded. */
data class Level(
    val id: Int,
    val chapter: String,
    val mode: GameMode,
    val difficulty: Int,      // 1..5
    val questionCount: Int,
    val timeLimitSec: Int
)

object LevelEngine {
    val chapters = listOf("Foundations", "Focus", "Calculation", "Logic Lab", "Mastermind")

    private val chapterModes = listOf(
        listOf(GameMode.MATH_SPRINT, GameMode.PATTERN, GameMode.MEMORY),
        listOf(GameMode.MEMORY, GameMode.MATH_SPRINT, GameMode.CROSS_MATH),
        listOf(GameMode.MATH_SPRINT, GameMode.CROSS_MATH, GameMode.MAZE),
        listOf(GameMode.LOGIC, GameMode.PATTERN, GameMode.CLASSIC),
        listOf(GameMode.MAZE, GameMode.CLASSIC, GameMode.LOGIC, GameMode.REFLEX, GameMode.MEMORY)
    )

    val levels: List<Level> = (1..100).map { id ->
        val chapterIdx = (id - 1) / 20
        val within = (id - 1) % 20
        val modes = chapterModes[chapterIdx]
        val mode = modes[within % modes.size]
        val difficulty = (1 + chapterIdx + (within % 3) / 2).coerceIn(1, 5)
        val questionCount = (6 + difficulty * 2).coerceAtMost(16)
        val timeLimitSec = (90 - difficulty * 8).coerceAtLeast(35)
        Level(
            id = id,
            chapter = chapters[chapterIdx],
            mode = mode,
            difficulty = difficulty,
            questionCount = questionCount,
            timeLimitSec = timeLimitSec
        )
    }

    fun byId(id: Int): Level = levels.firstOrNull { it.id == id } ?: levels.first()

    fun starsFor(accuracy: Double): Int = when {
        accuracy >= 0.9 -> 3
        accuracy >= 0.7 -> 2
        accuracy >= 0.5 -> 1
        else -> 0
    }
}

/** Elo-style rating with a fixed baseline opponent of 1000. Documented and deterministic. */
object RatingEngine {
    const val K = 32

    fun expected(current: Int, opponent: Int = 1000): Double =
        1.0 / (1.0 + Math.pow(10.0, (opponent - current) / 400.0))

    /** [actual] is 1.0 for a win, 0.0 for a loss, or a fractional performance score. */
    fun update(current: Int, actual: Double): Int =
        (current + K * (actual - expected(current))).roundToInt().coerceIn(100, 3000)

    fun tier(rating: Int): String = when {
        rating >= 1700 -> "Master"
        rating >= 1550 -> "Diamond"
        rating >= 1400 -> "Platinum"
        rating >= 1250 -> "Gold"
        rating >= 1100 -> "Silver"
        else -> "Bronze"
    }
}

data class DailyChallenge(
    val id: String,
    val title: String,
    val mode: GameMode,
    val difficulty: Int
)

object DailyEngine {
    fun seed(epochDay: Long): Long = epochDay * 2_654_435_761L

    fun challenges(epochDay: Long): List<DailyChallenge> = listOf(
        DailyChallenge("math", "Daily Math", GameMode.MATH_SPRINT, 2),
        DailyChallenge("logic", "Daily Logic", GameMode.LOGIC, 2),
        DailyChallenge("memory", "Daily Memory", GameMode.MEMORY, 2),
        DailyChallenge("speed", "Daily Speed", GameMode.REFLEX, 2),
        DailyChallenge("mixed", "Daily Mixed", GameMode.PATTERN, 3)
    )
}

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_game", "First Steps", "Finish your first game") { it.profile.gamesPlayed >= 1 },
        Achievement("ten_games", "Getting Warm", "Play 10 games") { it.profile.gamesPlayed >= 10 },
        Achievement("fifty_games", "Dedicated", "Play 50 games") { it.profile.gamesPlayed >= 50 },
        Achievement("streak_10", "On Fire", "Reach a 10-answer streak") { it.profile.bestStreak >= 10 },
        Achievement("streak_25", "Unstoppable", "Reach a 25-answer streak") { it.profile.bestStreak >= 25 },
        Achievement("stars_30", "Star Collector", "Earn 30 stars in the campaign") {
            it.levelProgress.values.sumOf { p -> p.stars } >= 30
        },
        Achievement("level_10", "Climber", "Complete 10 campaign levels") {
            it.levelProgress.values.count { p -> p.completed } >= 10
        },
        Achievement("level_50", "Halfway", "Complete 50 campaign levels") {
            it.levelProgress.values.count { p -> p.completed } >= 50
        },
        Achievement("gold_tier", "Gold Standard", "Reach Gold tier") {
            it.profile.ratings.overall >= 1250
        },
        Achievement("diamond_tier", "Diamond Mind", "Reach Diamond tier") {
            it.profile.ratings.overall >= 1550
        },
        Achievement("daily_7", "Daily Habit", "Reach a 7-day streak") { it.profile.streakDays >= 7 },
        Achievement("level_100", "Completionist", "Complete all 100 levels") {
            it.levelProgress.values.count { p -> p.completed } >= 100
        }
    )

    fun newlyUnlocked(state: AppState): List<Achievement> =
        all.filter { it.id !in state.unlockedAchievements && it.check(state) }
}
