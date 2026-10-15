package com.mindflow.app.game

import kotlin.random.Random

/** Operators available inside a Cross-Math grid. */
enum class Op(val symbol: String) {
    ADD("+"), SUB("−"), MUL("×");

    fun apply(a: Int, b: Int): Int = when (this) {
        ADD -> a + b
        SUB -> a - b
        MUL -> a * b
    }
}

/**
 * A Cross-Math puzzle: a square grid whose every row and column evaluates (strictly left-to-right /
 * top-to-bottom) to its target. Some cells are given; the rest are blank for the player to fill.
 *
 * Guarantees, enforced at generation time by [CrossMath.generate]:
 *  - a solution exists (the generator starts from one),
 *  - the solution is **unique** given the givens (verified by a backtracking solver).
 */
data class CrossMathPuzzle(
    val size: Int,
    val maxValue: Int,
    val solution: List<List<Int>>,
    /** rowOps[r][c] sits between cell (r,c) and (r,c+1) */
    val rowOps: List<List<Op>>,
    /** colOps[c][r] sits between cell (r,c) and (r+1,c) */
    val colOps: List<List<Op>>,
    val rowTargets: List<Int>,
    val colTargets: List<Int>,
    /** cells the player must fill */
    val blanks: Set<Pair<Int, Int>>
) {
    fun isGiven(r: Int, c: Int): Boolean = (r to c) !in blanks
    fun given(r: Int, c: Int): Int = solution[r][c]
    val blankCount: Int get() = blanks.size
}

object CrossMath {

    fun generate(difficulty: Int, rnd: Random): CrossMathPuzzle {
        val n = 3
        val d = difficulty.coerceIn(1, 5)
        val maxValue = if (d <= 2) 6 else 9
        val opsPool = if (d <= 2) listOf(Op.ADD, Op.SUB) else listOf(Op.ADD, Op.SUB, Op.MUL)

        val values = List(n) { List(n) { rnd.nextInt(1, maxValue + 1) } }
        var rowOps = List(n) { List(n - 1) { opsPool.random(rnd) } }
        var colOps = List(n) { List(n - 1) { opsPool.random(rnd) } }

        // Keep every target non-negative: replace subtracting operators while a result is negative.
        rowOps = rowOps.mapIndexed { r, ops -> fixNegative(values[r], ops) }
        colOps = colOps.mapIndexed { c, ops -> fixNegative(List(n) { values[it][c] }, ops) }

        val rowTargets = List(n) { r -> evalSeq(values[r], rowOps[r]) }
        val colTargets = List(n) { c -> evalSeq(List(n) { values[it][c] }, colOps[c]) }

        // Remove givens one at a time, keeping the puzzle uniquely solvable.
        val targetBlanks = when (d) { 1 -> 2; 2 -> 3; 3 -> 4; 4 -> 5; else -> 6 }
        val blanks = mutableSetOf<Pair<Int, Int>>()
        val order = (0 until n).flatMap { r -> (0 until n).map { r to it } }.shuffled(rnd)
        for (cell in order) {
            if (blanks.size >= targetBlanks) break
            blanks.add(cell)
            val candidate = CrossMathPuzzle(
                n, maxValue, values, rowOps, colOps, rowTargets, colTargets, blanks.toSet()
            )
            if (countSolutions(candidate, limit = 2) != 1) blanks.remove(cell)
        }

        return CrossMathPuzzle(
            n, maxValue, values, rowOps, colOps, rowTargets, colTargets, blanks.toSet()
        )
    }

    private fun fixNegative(vals: List<Int>, opsIn: List<Op>): List<Op> {
        val ops = opsIn.toMutableList()
        var guard = 0
        while (evalSeq(vals, ops) < 0 && guard < 6) {
            val i = ops.indexOfFirst { it == Op.SUB }
            if (i < 0) break
            ops[i] = Op.ADD
            guard++
        }
        return ops
    }

    fun evalSeq(vals: List<Int>, ops: List<Op>): Int {
        var acc = vals[0]
        for (i in ops.indices) acc = ops[i].apply(acc, vals[i + 1])
        return acc
    }

    /** Counts solutions up to [limit] using row-major backtracking with row/column pruning. */
    fun countSolutions(p: CrossMathPuzzle, limit: Int = 2): Int {
        val n = p.size
        val grid = Array(n) { arrayOfNulls<Int>(n) }
        for (r in 0 until n) for (c in 0 until n) if (p.isGiven(r, c)) grid[r][c] = p.solution[r][c]

        var count = 0
        fun rowComplete(r: Int): Boolean = (0 until n).all { grid[r][it] != null }
        fun colComplete(c: Int): Boolean = (0 until n).all { grid[it][c] != null }

        fun dfs(idx: Int) {
            if (count >= limit) return
            if (idx == n * n) { count++; return }
            val r = idx / n
            val c = idx % n
            val existing = grid[r][c]
            if (existing != null) {
                if (c == n - 1 && evalSeq(List(n) { grid[r][it]!! }, p.rowOps[r]) != p.rowTargets[r]) return
                if (r == n - 1 && evalSeq(List(n) { grid[it][c]!! }, p.colOps[c]) != p.colTargets[c]) return
                dfs(idx + 1)
                return
            }
            for (v in 1..p.maxValue) {
                grid[r][c] = v
                var ok = true
                if (c == n - 1 && evalSeq(List(n) { grid[r][it]!! }, p.rowOps[r]) != p.rowTargets[r]) ok = false
                if (ok && r == n - 1 && evalSeq(List(n) { grid[it][c]!! }, p.colOps[c]) != p.colTargets[c]) ok = false
                if (ok) dfs(idx + 1)
                grid[r][c] = null
                if (count >= limit) return
            }
        }
        dfs(0)
        return count
    }

    /** True when [grid] is completely filled and every row and column hits its target. */
    fun isSolved(grid: List<List<Int?>>, p: CrossMathPuzzle): Boolean {
        val n = p.size
        if (grid.size != n || grid.any { it.size != n }) return false
        if (grid.any { row -> row.any { it == null } }) return false
        val values = grid.map { row -> row.map { it!! } }
        for (r in 0 until n) if (evalSeq(values[r], p.rowOps[r]) != p.rowTargets[r]) return false
        for (c in 0 until n) if (evalSeq(List(n) { values[it][c] }, p.colOps[c]) != p.colTargets[c]) return false
        return true
    }
}
