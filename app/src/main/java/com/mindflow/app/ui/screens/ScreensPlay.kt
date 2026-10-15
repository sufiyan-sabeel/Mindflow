package com.mindflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mindflow.app.data.GameMode
import com.mindflow.app.data.GameResult
import com.mindflow.app.domain.LevelEngine
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.domain.Time
import com.mindflow.app.game.GameContent
import com.mindflow.app.game.Question
import com.mindflow.app.ui.AppViewModel
import com.mindflow.app.ui.components.AnswerFeedback
import com.mindflow.app.ui.components.KeypadValue
import com.mindflow.app.ui.components.MfButton
import com.mindflow.app.ui.components.MfCard
import com.mindflow.app.ui.components.MfOutlineButton
import com.mindflow.app.ui.components.ProgressBar
import com.mindflow.app.ui.components.StarRow
import com.mindflow.app.ui.theme.AccentLime
import com.mindflow.app.ui.theme.Background
import com.mindflow.app.ui.theme.BorderColor
import com.mindflow.app.ui.theme.ErrorRed
import com.mindflow.app.ui.theme.PrimaryPurple
import com.mindflow.app.ui.theme.Success
import com.mindflow.app.ui.theme.Surface1
import com.mindflow.app.ui.theme.Surface2
import com.mindflow.app.ui.theme.TextPrimary
import com.mindflow.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class SessionMetrics(
    val correct: Int,
    val total: Int,
    val bestStreak: Int,
    val avgResponseMs: Long
)

@Composable
fun PlayScreen(
    vm: AppViewModel,
    modeId: String,
    levelId: Int,
    dailyId: String?,
    onExit: () -> Unit
) {
    val mode = GameMode.fromId(modeId)
    val level = if (levelId > 0) LevelEngine.byId(levelId) else null
    val difficulty = level?.difficulty ?: 2
    val count = level?.questionCount ?: 8
    val timeLimit = level?.timeLimitSec ?: 60

    var metrics by remember { mutableStateOf<SessionMetrics?>(null) }
    var recorded by remember { mutableStateOf(false) }
    val seed = remember { Random(System.nanoTime()) }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            PlayTopBar(
                title = level?.let { "Level ${it.id}" } ?: mode.title,
                subtitle = if (level != null) level.chapter else "Free play",
                onExit = onExit
            )
            Box(Modifier.fillMaxSize()) {
                when (mode) {
                    GameMode.MATH_SPRINT -> MathSprintGame(difficulty, count, timeLimit, seed) { metrics = it }
                    GameMode.PATTERN, GameMode.LOGIC -> MultipleChoiceGame(mode, difficulty, count, timeLimit, seed) { metrics = it }
                    GameMode.MEMORY -> MemoryGame(difficulty, count, seed) { metrics = it }
                    GameMode.REFLEX -> ReflexGame(count, seed) { metrics = it }
                }
            }
        }

        metrics?.let { m ->
            ResultOverlay(
                mode = mode,
                metrics = m,
                levelId = level?.id,
                difficulty = difficulty,
                currentRating = vm.state.value.profile.ratings.get(mode.skill),
                onRecord = {
                    if (!recorded) {
                        recorded = true
                        val accuracy = if (m.total == 0) 0.0 else m.correct.toDouble() / m.total
                        val stars = if (level != null) LevelEngine.starsFor(accuracy) else LevelEngine.starsFor(accuracy)
                        val score = m.correct * 100 + m.bestStreak * 25 - (m.total - m.correct) * 20
                        val xp = (m.correct * 10 + m.bestStreak * 5 + stars * 20).coerceAtLeast(5)
                        vm.recordResult(
                            GameResult(
                                mode = mode,
                                levelId = level?.id,
                                correct = m.correct,
                                total = m.total,
                                bestStreak = m.bestStreak,
                                avgResponseMs = m.avgResponseMs,
                                score = score,
                                stars = stars,
                                xp = xp,
                                epochDay = Time.epochDay(),
                                timestamp = System.currentTimeMillis()
                            ),
                            dailyChallengeId = dailyId
                        )
                    }
                },
                onRetry = { metrics = null; recorded = false },
                onHome = onExit
            )
        }
    }
}

@Composable
private fun PlayTopBar(title: String, subtitle: String, onExit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Surface2,
            shape = CircleShape,
            modifier = Modifier.size(40.dp).clickable { onExit() },
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("✕", color = TextPrimary) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun MathSprintGame(
    difficulty: Int,
    count: Int,
    timeLimitSec: Int,
    seed: Random,
    onFinish: (SessionMetrics) -> Unit
) {
    var question by remember { mutableStateOf(GameContent.mathQuestion(difficulty, seed)) }
    var input by remember { mutableStateOf("") }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var timeLeft by remember { mutableIntStateOf(timeLimitSec) }
    var finished by remember { mutableStateOf(false) }
    var lastFeedback by remember { mutableStateOf<Boolean?>(null) }
    val responseTimes = remember { mutableListOf<Long>() }
    var questionShownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun finish() {
        if (finished) return
        finished = true
        onFinish(
            SessionMetrics(
                correct = correct,
                total = total,
                bestStreak = bestStreak,
                avgResponseMs = if (responseTimes.isEmpty()) 0 else responseTimes.average().toLong()
            )
        )
    }

    fun submit() {
        val value = input.toIntOrNull() ?: return
        val elapsed = System.currentTimeMillis() - questionShownAt
        responseTimes.add(elapsed)
        total += 1
        if (question.isCorrect(value)) {
            correct += 1; streak += 1; bestStreak = maxOf(bestStreak, streak); lastFeedback = true
        } else {
            streak = 0; lastFeedback = false
        }
        input = ""
        if (total >= count) finish() else {
            question = GameContent.mathQuestion(difficulty, seed)
            questionShownAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(Unit) {
        while (timeLeft > 0 && !finished) {
            delay(1000)
            timeLeft -= 1
        }
        finish()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ $timeLeft s", color = AccentLime, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("$correct/$total", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(timeLeft.toFloat() / timeLimitSec)
        Spacer(Modifier.height(24.dp))
        Text(question.prompt, style = MaterialTheme.typography.displayLarge, color = TextPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        lastFeedback?.let { fb ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AnswerFeedback(fb) }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (input.isEmpty()) "—" else input,
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryPurple,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        KeypadValue(onDigit = { input = (input + it).take(6) }, onBackspace = { input = input.dropLast(1) })
        Spacer(Modifier.height(8.dp))
        MfButton("Submit", { submit() }, enabled = input.isNotEmpty(), color = AccentLime)
    }
}

@Composable
private fun MultipleChoiceGame(
    mode: GameMode,
    difficulty: Int,
    count: Int,
    timeLimitSec: Int,
    seed: Random,
    onFinish: (SessionMetrics) -> Unit
) {
    fun next(): Question = if (mode == GameMode.PATTERN)
        GameContent.patternQuestion(difficulty, seed) else GameContent.logicQuestion(difficulty, seed)

    var question by remember { mutableStateOf(next()) }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var chosen by remember { mutableStateOf<Int?>(null) }
    var timeLeft by remember { mutableIntStateOf(timeLimitSec) }
    var finished by remember { mutableStateOf(false) }
    val responseTimes = remember { mutableListOf<Long>() }
    var shownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun finish() {
        if (finished) return
        finished = true
        onFinish(
            SessionMetrics(
                correct = correct, total = total, bestStreak = bestStreak,
                avgResponseMs = if (responseTimes.isEmpty()) 0 else responseTimes.average().toLong()
            )
        )
    }

    fun choose(value: Int) {
        if (chosen != null) return
        chosen = value
        responseTimes.add(System.currentTimeMillis() - shownAt)
        total += 1
        if (question.isCorrect(value)) { correct += 1; streak += 1; bestStreak = maxOf(bestStreak, streak) } else streak = 0
    }

    LaunchedEffect(chosen) {
        if (chosen != null) {
            delay(650)
            if (total >= count) finish() else {
                question = next(); chosen = null; shownAt = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(Unit) {
        while (timeLeft > 0 && !finished) { delay(1000); timeLeft -= 1 }
        finish()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ $timeLeft s", color = AccentLime, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("$correct/$total", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(timeLeft.toFloat() / timeLimitSec)
        Spacer(Modifier.height(28.dp))
        Text(question.prompt, style = MaterialTheme.typography.displayLarge, color = TextPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        question.choices.forEach { option ->
            val isChosen = chosen == option
            val bg = when {
                isChosen && option == question.answer -> Success.copy(alpha = 0.25f)
                isChosen -> ErrorRed.copy(alpha = 0.25f)
                chosen != null && option == question.answer -> Success.copy(alpha = 0.15f)
                else -> Surface1
            }
            Surface(
                color = bg,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { choose(option) }
            ) {
                Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                    Text("$option", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun MemoryGame(
    difficulty: Int,
    count: Int,
    seed: Random,
    onFinish: (SessionMetrics) -> Unit
) {
    val rounds = (count / 2).coerceIn(3, 6)
    var round by remember { mutableIntStateOf(0) }
    var sequence by remember { mutableStateOf(GameContent.memorySequence(difficulty, seed)) }
    var phase by remember { mutableStateOf("show") } // show | recall
    var input by remember { mutableStateOf("") }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val responseTimes = remember { mutableListOf<Long>() }
    var shownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun finish() {
        if (finished) return
        finished = true
        onFinish(
            SessionMetrics(
                correct = correct, total = total, bestStreak = bestStreak,
                avgResponseMs = if (responseTimes.isEmpty()) 0 else responseTimes.average().toLong()
            )
        )
    }

    LaunchedEffect(phase, round) {
        if (phase == "show") {
            delay(1200L + sequence.size * 350L)
            phase = "recall"
            shownAt = System.currentTimeMillis()
        }
    }

    fun submit() {
        if (input.isEmpty()) return
        responseTimes.add(System.currentTimeMillis() - shownAt)
        total += 1
        if (input == sequence.joinToString("")) { correct += 1; streak += 1; bestStreak = maxOf(bestStreak, streak) } else streak = 0
        input = ""
        if (round + 1 >= rounds) finish() else {
            round += 1
            sequence = GameContent.memorySequence(difficulty, seed)
            phase = "show"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${round + 1}/$rounds", color = AccentLime, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("$correct/$total", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        if (phase == "show") {
            Text("Memorise", style = MaterialTheme.typography.titleLarge, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(
                sequence.joinToString("  "),
                style = MaterialTheme.typography.displayLarge,
                color = PrimaryPurple,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            Text("Recall the sequence", style = MaterialTheme.typography.titleLarge, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Text(
                if (input.isEmpty()) "—" else input,
                style = MaterialTheme.typography.displayLarge,
                color = AccentLime,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            KeypadValue(onDigit = { input = (input + it).take(sequence.size) }, onBackspace = { input = input.dropLast(1) })
            Spacer(Modifier.height(8.dp))
            MfButton("Submit", { submit() }, enabled = input.length == sequence.size, color = AccentLime)
        }
        if (phase == "show") Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ReflexGame(
    count: Int,
    seed: Random,
    onFinish: (SessionMetrics) -> Unit
) {
    val rounds = count.coerceIn(4, 8)
    var round by remember { mutableIntStateOf(0) }
    var waiting by remember { mutableStateOf(true) }
    var targetVisible by remember { mutableStateOf(false) }
    var tooEarly by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val responseTimes = remember { mutableListOf<Long>() }
    var shownAt by remember { mutableLongStateOf(0) }
    var bestStreak by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }

    fun finish() {
        if (finished) return
        finished = true
        onFinish(
            SessionMetrics(
                correct = correct, total = total, bestStreak = bestStreak,
                avgResponseMs = if (responseTimes.isEmpty()) 0 else responseTimes.average().toLong()
            )
        )
    }

    LaunchedEffect(round) {
        waiting = true; targetVisible = false; tooEarly = false
        delay(700L + seed.nextLong(1600L))
        waiting = false; targetVisible = true; shownAt = System.currentTimeMillis()
    }

    fun tap() {
        if (waiting && !targetVisible) {
            // too early — counts as a miss
            tooEarly = true; total += 1; streak = 0
            if (round + 1 >= rounds) finish() else round += 1
            return
        }
        if (targetVisible) {
            responseTimes.add(System.currentTimeMillis() - shownAt)
            total += 1; correct += 1; streak += 1; bestStreak = maxOf(bestStreak, streak)
            if (round + 1 >= rounds) finish() else round += 1
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${round + 1}/$rounds", color = AccentLime, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text("$correct/$total", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar((round + 1).toFloat() / rounds)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(if (targetVisible) AccentLime else Surface2)
                .clickable { tap() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    targetVisible -> "TAP"
                    tooEarly -> "Too early"
                    else -> "Wait…"
                },
                color = if (targetVisible) Background else TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Tap the circle the moment it turns green.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultOverlay(
    mode: GameMode,
    metrics: SessionMetrics,
    levelId: Int?,
    difficulty: Int,
    currentRating: Int,
    onRecord: () -> Unit,
    onRetry: () -> Unit,
    onHome: () -> Unit
) {
    LaunchedEffect(Unit) { onRecord() }

    val accuracy = if (metrics.total == 0) 0.0 else metrics.correct.toDouble() / metrics.total
    val stars = LevelEngine.starsFor(accuracy)
    val newRating = RatingEngine.update(currentRating, accuracy)
    val delta = newRating - currentRating

    Box(Modifier.fillMaxSize().background(Background.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (accuracy >= 0.7) "Nice work" else "Keep training", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            StarRow(stars, 18)
            Spacer(Modifier.height(20.dp))
            MfCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ResultStat("Accuracy", "${(accuracy * 100).toInt()}%")
                    ResultStat("Correct", "${metrics.correct}/${metrics.total}")
                    ResultStat("Streak", "${metrics.bestStreak}")
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ResultStat("Avg time", if (metrics.avgResponseMs > 0) "${metrics.avgResponseMs} ms" else "—")
                    ResultStat("Rating", "${newRating}")
                    ResultStat("Δ", if (delta >= 0) "+$delta" else "$delta")
                }
            }
            Spacer(Modifier.height(20.dp))
            MfButton("Play again", onRetry)
            Spacer(Modifier.height(10.dp))
            MfOutlineButton("Back to Arena", onHome)
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentLime)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
