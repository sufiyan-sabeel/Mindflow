package com.mindflow.app

import com.mindflow.app.data.GameMode
import com.mindflow.app.domain.LevelEngine
import com.mindflow.app.game.CrossMath
import com.mindflow.app.game.GameContent
import com.mindflow.app.game.MathMaze
import com.mindflow.app.game.MiniSudoku
import com.mindflow.app.game.PathCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {

    // ---------- Cross-Math ----------

    @Test
    fun crossMath_solution_satisfiesItsOwnTargets() {
        val rnd = Random(1)
        repeat(30) { i ->
            val p = CrossMath.generate((i % 5) + 1, rnd)
            val grid = p.solution.map { row -> row.map { it as Int? } }
            assertTrue("solution must solve its own puzzle", CrossMath.isSolved(grid, p))
        }
    }

    @Test
    fun crossMath_puzzleHasExactlyOneSolution() {
        val rnd = Random(7)
        repeat(40) { i ->
            val p = CrossMath.generate((i % 5) + 1, rnd)
            assertEquals("puzzle must be uniquely solvable", 1, CrossMath.countSolutions(p, limit = 2))
        }
    }

    @Test
    fun crossMath_hasBlanks_andWithinValueRange() {
        val rnd = Random(11)
        repeat(20) { i ->
            val p = CrossMath.generate((i % 5) + 1, rnd)
            assertTrue("puzzle should have blanks to fill", p.blankCount >= 1)
            for (row in p.solution) for (v in row) {
                assertTrue("value $v out of range", v in 1..p.maxValue)
            }
        }
    }

    @Test
    fun crossMath_wrongGrid_isNotSolved() {
        val rnd = Random(3)
        val p = CrossMath.generate(2, rnd)
        // Perturb one cell so at least one equation must fail.
        val grid = p.solution.map { row -> row.map { it as Int? } }.toMutableList()
        val firstRow = grid[0].toMutableList()
        firstRow[0] = if (firstRow[0] == 1) 2 else 1
        grid[0] = firstRow
        assertTrue("a perturbed grid must not validate", !CrossMath.isSolved(grid, p))
    }

    // ---------- Math Maze ----------

    @Test
    fun maze_targetIsReachable() {
        val rnd = Random(21)
        repeat(30) { i ->
            val p = MathMaze.generate((i % 5) + 1, rnd)
            assertTrue("target must be achievable", MathMaze.countPaths(p) >= 1)
        }
    }

    @Test
    fun maze_solutionPath_isValid_andSumsToTarget() {
        val rnd = Random(22)
        repeat(30) { i ->
            val p = MathMaze.generate((i % 5) + 1, rnd)
            assertTrue(MathMaze.validatePath(p.solutionPath, p) is PathCheck.Valid)
            assertEquals(p.target, MathMaze.pathSum(p.solutionPath, p))
        }
    }

    @Test
    fun maze_startAndEnd_neverBlocked() {
        val rnd = Random(23)
        repeat(20) { i ->
            val p = MathMaze.generate((i % 5) + 1, rnd)
            assertTrue((0 to 0) !in p.blocked)
            val end = (p.size - 1) to (p.size - 1)
            assertTrue(end !in p.blocked)
        }
    }

    @Test
    fun maze_invalidPath_isRejected() {
        val rnd = Random(24)
        val p = MathMaze.generate(1, rnd)
        // A single-cell path (start only) cannot be the solution.
        val bad = listOf(0 to 0)
        assertTrue(MathMaze.validatePath(bad, p) is PathCheck.Invalid)
        // A diagonal jump is not allowed.
        val diagonal = listOf(0 to 0, 1 to 1)
        assertTrue(MathMaze.validatePath(diagonal, p) is PathCheck.Invalid)
    }

    // ---------- Mini Sudoku ----------

    @Test
    fun sudoku_solution_isValid_andGivensMatchSolution() {
        val rnd = Random(31)
        repeat(12) { i ->
            val p = MiniSudoku.generate((i % 5) + 1, rnd)
            assertTrue("solution must be a valid grid", MiniSudoku.isSolved(p.solution, p))
            for (r in 0 until p.size) for (c in 0 until p.size) {
                if (p.isGiven(r, c)) {
                    assertEquals("given must equal the solution", p.solution[r][c], p.givens[r][c])
                }
            }
        }
    }

    @Test
    fun sudoku_puzzleHasExactlyOneSolution() {
        val rnd = Random(32)
        repeat(12) { i ->
            val p = MiniSudoku.generate((i % 5) + 1, rnd)
            val mutable = p.givens.map { it.toMutableList() }
            assertEquals(1, MiniSudoku.countSolutions(mutable, p.size, p.boxRows, p.boxCols, limit = 2))
        }
    }

    @Test
    fun sudoku_usesExpectedSizes() {
        val rnd = Random(33)
        assertEquals(4, MiniSudoku.generate(1, rnd).size)
        assertEquals(4, MiniSudoku.generate(3, rnd).size)
        assertEquals(6, MiniSudoku.generate(5, rnd).size)
    }

    @Test
    fun sudoku_incompleteGrid_isNotSolved() {
        val rnd = Random(34)
        val p = MiniSudoku.generate(1, rnd)
        assertTrue(!MiniSudoku.isSolved(p.givens, p))
    }

    // ---------- Question mix ----------

    @Test
    fun percentQuestion_answerIsWholeAndCorrect() {
        val rnd = Random(41)
        repeat(200) {
            val q = GameContent.percentQuestion(3, rnd)
            // prompt is "<pct>% of <base>"
            val pct = q.prompt.substringBefore("%").toInt()
            val base = q.prompt.substringAfter("of ").toInt()
            assertEquals(base * pct / 100, q.answer)
            assertEquals("percentage answer must be whole", 0, (base * pct) % 100)
        }
    }

    @Test
    fun fractionQuestion_answerIsWholeAndCorrect() {
        val rnd = Random(42)
        repeat(200) {
            val q = GameContent.fractionQuestion(3, rnd)
            val frac = q.prompt.substringBefore(" of")
            val whole = q.prompt.substringAfter("of ").toInt()
            val num = frac.substringBefore("/").toInt()
            val den = frac.substringAfter("/").toInt()
            assertEquals(whole * num / den, q.answer)
            assertEquals(0, (whole * num) % den)
        }
    }

    @Test
    fun mathSprintQuestion_isAlwaysValid() {
        val rnd = Random(43)
        repeat(500) { i ->
            val q = GameContent.mathSprintQuestion((i % 5) + 1, rnd)
            assertTrue(q.prompt.isNotBlank())
        }
    }

    // ---------- Levels ----------

    @Test
    fun levels_coverEveryMode() {
        val modes = LevelEngine.levels.map { it.mode }.toSet()
        assertEquals(GameMode.entries.toSet(), modes)
        assertEquals(100, LevelEngine.levels.size)
    }

    @Test
    fun levels_unlockOrder_isSequential() {
        assertEquals(1, com.mindflow.app.data.AppState().highestUnlockedLevel)
        assertNotEquals(2, com.mindflow.app.data.AppState().highestUnlockedLevel)
    }
}
