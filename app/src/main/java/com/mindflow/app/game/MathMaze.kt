package com.mindflow.app.game

import kotlin.random.Random

/**
 * A Math Maze: a grid of numbers the player traverses from the top-left to the bottom-right using
 * only right/down moves, collecting values, so the running sum equals [target]. Some cells are
 * blocked. The target is derived from a real path, so a solution always exists.
 */
data class MazePuzzle(
    val size: Int,
    val cells: List<List<Int>>,
    val blocked: Set<Pair<Int, Int>>,
    val target: Int,
    val solutionPath: List<Pair<Int, Int>>
)

sealed class PathCheck {
    data object Valid : PathCheck()
    data class Invalid(val reason: String) : PathCheck()
}

object MathMaze {

    fun generate(difficulty: Int, rnd: Random): MazePuzzle {
        val d = difficulty.coerceIn(1, 5)
        val n = when (d) { 1, 2 -> 3; 3, 4 -> 4; else -> 5 }
        val cells = List(n) { List(n) { rnd.nextInt(1, 10) } }

        val blocked = mutableSetOf<Pair<Int, Int>>()
        val obstacleTarget = (d - 1).coerceIn(0, 4)
        var guard = 0
        while (blocked.size < obstacleTarget && guard < 300) {
            guard++
            val cell = rnd.nextInt(0, n) to rnd.nextInt(0, n)
            if (cell == (0 to 0) || cell == (n - 1 to n - 1)) continue
            if (cell in blocked) continue
            blocked.add(cell)
            if (!reachable(n, blocked)) blocked.remove(cell)
        }

        val path = randomPath(n, blocked, rnd)
            ?: (0 until n).map { it to it } // monotonic diagonal fallback (never blocked)
        val target = path.sumOf { cells[it.first][it.second] }

        return MazePuzzle(n, cells, blocked.toSet(), target, path)
    }

    fun reachable(n: Int, blocked: Set<Pair<Int, Int>>): Boolean {
        if ((0 to 0) in blocked || (n - 1 to n - 1) in blocked) return false
        val seen = Array(n) { BooleanArray(n) }
        seen[0][0] = true
        for (r in 0 until n) for (c in 0 until n) {
            if ((r to c) in blocked) { seen[r][c] = false; continue }
            if (r == 0 && c == 0) continue
            val fromAbove = r > 0 && seen[r - 1][c]
            val fromLeft = c > 0 && seen[r][c - 1]
            seen[r][c] = fromAbove || fromLeft
        }
        return seen[n - 1][n - 1]
    }

    private fun randomPath(n: Int, blocked: Set<Pair<Int, Int>>, rnd: Random): List<Pair<Int, Int>>? {
        val path = mutableListOf<Pair<Int, Int>>()
        fun dfs(r: Int, c: Int): Boolean {
            path.add(r to c)
            if (r == n - 1 && c == n - 1) return true
            val moves = mutableListOf<Pair<Int, Int>>()
            if (c + 1 < n && (r to c + 1) !in blocked) moves.add(r to c + 1)
            if (r + 1 < n && (r + 1 to c) !in blocked) moves.add(r + 1 to c)
            moves.shuffle(rnd)
            for (m in moves) if (dfs(m.first, m.second)) return true
            path.removeAt(path.size - 1)
            return false
        }
        return if (dfs(0, 0)) path.toList() else null
    }

    /** Number of monotonic paths whose collected sum equals the target. */
    fun countPaths(p: MazePuzzle): Int {
        val n = p.size
        val dp = Array(n) { Array(n) { HashMap<Int, Int>() } }
        for (r in 0 until n) for (c in 0 until n) {
            if ((r to c) in p.blocked) continue
            val v = p.cells[r][c]
            if (r == 0 && c == 0) { dp[0][0][v] = 1; continue }
            val acc = HashMap<Int, Int>()
            if (r > 0) dp[r - 1][c].forEach { (s, cnt) -> acc[s + v] = (acc[s + v] ?: 0) + cnt }
            if (c > 0) dp[r][c - 1].forEach { (s, cnt) -> acc[s + v] = (acc[s + v] ?: 0) + cnt }
            dp[r][c] = acc
        }
        return dp[n - 1][n - 1][p.target] ?: 0
    }

    fun pathSum(path: List<Pair<Int, Int>>, p: MazePuzzle): Int =
        path.sumOf { p.cells[it.first][it.second] }

    fun validatePath(path: List<Pair<Int, Int>>, p: MazePuzzle): PathCheck {
        if (path.isEmpty() || path.first() != (0 to 0)) return PathCheck.Invalid("Start at the top-left cell.")
        if (path.last() != (p.size - 1 to p.size - 1)) return PathCheck.Invalid("Finish at the bottom-right cell.")
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val dr = b.first - a.first; val dc = b.second - a.second
            if (!((dr == 1 && dc == 0) || (dr == 0 && dc == 1))) {
                return PathCheck.Invalid("Move only right or down, between adjacent cells.")
            }
        }
        if (path.any { it in p.blocked }) return PathCheck.Invalid("That route crosses a blocked cell.")
        val sum = pathSum(path, p)
        if (sum != p.target) return PathCheck.Invalid("Sum is $sum — the target is ${p.target}.")
        return PathCheck.Valid
    }
}
