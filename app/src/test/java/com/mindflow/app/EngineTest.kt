package com.mindflow.app

import com.mindflow.app.data.AppState
import com.mindflow.app.data.LevelProgress
import com.mindflow.app.domain.DailyEngine
import com.mindflow.app.domain.LevelEngine
import com.mindflow.app.domain.RatingEngine
import com.mindflow.app.game.GameContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    @Test
    fun buildChoices_containsAnswer_andIsUnique() {
        val rnd = Random(7)
        repeat(200) { i ->
            val answer = rnd.nextInt(-50, 50)
            val choices = GameContent.buildChoices(answer, rnd)
            assertEquals("4 choices expected", 4, choices.size)
            assertEquals("answer must be present", 1, choices.count { it == answer })
            assertEquals("choices must be unique", 4, choices.toSet().size)
        }
    }

    @Test
    fun patternQuestion_hasAnswerAmongChoices() {
        val rnd = Random(11)
        repeat(200) { i ->
            val q = GameContent.patternQuestion((i % 5) + 1, rnd)
            assertTrue(q.choices.contains(q.answer))
        }
    }

    @Test
    fun mathQuestion_answerIsConsistentWithPrompt() {
        val rnd = Random(3)
        repeat(500) { i ->
            val q = GameContent.mathQuestion((i % 5) + 1, rnd)
            // Every generated prompt is of the form "a <op> b"; recompute and compare.
            val parts = q.prompt.replace("−", "-").replace("×", "*").replace("÷", "/").split(" ")
            assertEquals(3, parts.size)
            val a = parts[0].toInt()
            val b = parts[2].toInt()
            val expected = when (parts[1]) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> a / b
                else -> error("unexpected op ${parts[1]}")
            }
            assertEquals("prompt ${q.prompt}", expected, q.answer)
            if (parts[1] == "/") assertEquals("division must be exact", 0, a % b)
        }
    }

    @Test
    fun memorySequence_growsWithDifficulty() {
        val rnd = Random(5)
        val easy = GameContent.memorySequence(1, rnd).size
        val hard = GameContent.memorySequence(5, rnd).size
        assertTrue("harder sequences should be longer", hard > easy)
    }

    @Test
    fun starsFor_thresholds() {
        assertEquals(3, LevelEngine.starsFor(0.95))
        assertEquals(2, LevelEngine.starsFor(0.75))
        assertEquals(1, LevelEngine.starsFor(0.55))
        assertEquals(0, LevelEngine.starsFor(0.3))
    }

    @Test
    fun rating_increasesOnWin_decreasesOnLoss() {
        val win = RatingEngine.update(1000, 1.0)
        val loss = RatingEngine.update(1000, 0.0)
        assertTrue("win should raise rating", win > 1000)
        assertTrue("loss should lower rating", loss < 1000)
    }

    @Test
    fun tier_boundaries() {
        assertEquals("Bronze", RatingEngine.tier(900))
        assertEquals("Silver", RatingEngine.tier(1150))
        assertEquals("Gold", RatingEngine.tier(1300))
        assertEquals("Platinum", RatingEngine.tier(1450))
        assertEquals("Diamond", RatingEngine.tier(1600))
        assertEquals("Master", RatingEngine.tier(1800))
    }

    @Test
    fun dailyChallenges_areDeterministicPerDay() {
        val a = DailyEngine.challenges(20000).map { it.id }
        val b = DailyEngine.challenges(20000).map { it.id }
        assertEquals(a, b)
        assertEquals(DailyEngine.seed(20000), DailyEngine.seed(20000))
    }

    @Test
    fun levelEngine_hasOneHundredLevels() {
        assertEquals(100, LevelEngine.levels.size)
        assertEquals(1, LevelEngine.levels.first().id)
        assertEquals(100, LevelEngine.levels.last().id)
        LevelEngine.levels.forEach { assertTrue(it.difficulty in 1..5) }
    }

    @Test
    fun highestUnlockedLevel_advancesSequentially() {
        val state = AppState(
            levelProgress = mapOf(
                "1" to LevelProgress(stars = 3, completed = true),
                "2" to LevelProgress(stars = 2, completed = true)
            )
        )
        assertEquals(3, state.highestUnlockedLevel)
    }
}
