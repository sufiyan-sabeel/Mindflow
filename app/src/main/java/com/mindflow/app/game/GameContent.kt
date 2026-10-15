package com.mindflow.app.game

import kotlin.random.Random

/** A single question with a validated integer answer. [choices] is populated for multiple-choice modes. */
data class Question(
    val prompt: String,
    val answer: Int,
    val choices: List<Int> = emptyList()
) {
    fun isCorrect(value: Int): Boolean = value == answer
}

/**
 * Deterministic question generators. Every generator takes a [Random] so callers can supply a
 * seeded instance (daily challenges) or a fresh one (free play). Generators guarantee a valid,
 * solvable question with a unique correct answer.
 */
object GameContent {

    fun buildChoices(answer: Int, rnd: Random, count: Int = 4): List<Int> {
        val set = linkedSetOf(answer)
        var guard = 0
        while (set.size < count && guard < 200) {
            guard++
            val magnitude = 1 + rnd.nextInt(1, 12)
            val delta = if (rnd.nextBoolean()) magnitude else -magnitude
            val candidate = answer + delta
            if (candidate != answer) set.add(candidate)
        }
        // Fallback: pad with sequential values if randomness collided.
        var filler = answer + 1
        while (set.size < count) { set.add(filler); filler++ }
        return set.toList().shuffled(rnd)
    }

    fun mathQuestion(difficulty: Int, rnd: Random): Question {
        val d = difficulty.coerceIn(1, 5)
        val max = 8 + d * 6
        val op = when (d) {
            1 -> listOf("+", "-").random(rnd)
            2 -> listOf("+", "-", "×").random(rnd)
            3 -> listOf("+", "-", "×", "÷").random(rnd)
            else -> listOf("+", "-", "×", "÷", "×").random(rnd)
        }
        return when (op) {
            "+" -> {
                val a = rnd.nextInt(1, max + 1); val b = rnd.nextInt(1, max + 1)
                Question("$a + $b", a + b)
            }
            "-" -> {
                val a = rnd.nextInt(2, max + 1); val b = rnd.nextInt(1, a)
                Question("$a − $b", a - b)
            }
            "×" -> {
                val a = rnd.nextInt(2, (max / 2).coerceAtLeast(3) + 1)
                val b = rnd.nextInt(2, (max / 2).coerceAtLeast(3) + 1)
                Question("$a × $b", a * b)
            }
            else -> {
                val b = rnd.nextInt(2, (max / 2).coerceAtLeast(3) + 1)
                val q = rnd.nextInt(2, max + 1)
                Question("${b * q} ÷ $b", q)
            }
        }
    }

    /** Arithmetic / geometric sequence with the final term missing. */
    fun patternQuestion(difficulty: Int, rnd: Random): Question {
        val d = difficulty.coerceIn(1, 5)
        val geometric = d >= 4 && rnd.nextBoolean()
        val terms: List<Int>
        if (geometric) {
            val start = rnd.nextInt(1, 4)
            val ratio = rnd.nextInt(2, 3 + d / 2)
            terms = (0..4).map { start * pow(ratio, it) }
        } else {
            val start = rnd.nextInt(1, 10)
            val step = rnd.nextInt(2, 3 + d * 2)
            terms = (0..4).map { start + it * step }
        }
        val answer = terms.last()
        val prompt = terms.dropLast(1).joinToString(", ") + ", ?"
        return Question(prompt, answer, buildChoices(answer, rnd))
    }

    /** Deduction / relationship question with a single correct option. */
    fun logicQuestion(difficulty: Int, rnd: Random): Question {
        val d = difficulty.coerceIn(1, 5)
        return when (rnd.nextInt(3)) {
            0 -> {
                // Sum of digits of a number.
                val n = rnd.nextInt(11, 60 + d * 40)
                val answer = n.toString().sumOf { it - '0' }
                Question("Sum of digits of $n", answer, buildChoices(answer, rnd))
            }
            1 -> {
                // Largest of a set.
                val set = (0..3).map { rnd.nextInt(1, 40 + d * 20) }.distinct()
                val answer = set.max()
                Question("Largest number", answer, set.sorted().toMutableList())
            }
            else -> {
                // Squares sequence.
                val start = rnd.nextInt(1, 4)
                val terms = (0..4).map { val v = start + it; v * v }
                val answer = terms.last()
                Question(terms.dropLast(1).joinToString(", ") + ", ?", answer, buildChoices(answer, rnd))
            }
        }
    }

    /** Digit sequence to memorise, length scales with difficulty. */
    fun memorySequence(difficulty: Int, rnd: Random): List<Int> {
        val d = difficulty.coerceIn(1, 5)
        val length = (3 + d).coerceAtMost(9)
        return (0 until length).map { rnd.nextInt(0, 10) }
    }

    private fun pow(base: Int, exp: Int): Int {
        var r = 1
        repeat(exp) { r *= base }
        return r
    }
}
