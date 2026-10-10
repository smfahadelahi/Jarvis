package com.jarvis.ai

import com.jarvis.ai.core.tools.ArithmeticEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArithmeticEvaluatorTest {

    @Test
    fun `addition and subtraction`() {
        assertEquals(7.0, ArithmeticEvaluator.evaluate("3 + 4"), 1e-9)
        assertEquals(-1.0, ArithmeticEvaluator.evaluate("3 - 4"), 1e-9)
    }

    @Test
    fun `multiplication and division`() {
        assertEquals(12.0, ArithmeticEvaluator.evaluate("3 * 4"), 1e-9)
        assertEquals(2.5, ArithmeticEvaluator.evaluate("5 / 2"), 1e-9)
    }

    @Test
    fun `precedence holds`() {
        assertEquals(14.0, ArithmeticEvaluator.evaluate("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, ArithmeticEvaluator.evaluate("(2 + 3) * 4"), 1e-9)
    }

    @Test
    fun `exponent is right associative`() {
        assertEquals(512.0, ArithmeticEvaluator.evaluate("2 ^ 3 ^ 2"), 1e-9)
    }

    @Test
    fun `unary minus and modulo`() {
        assertEquals(-6.0, ArithmeticEvaluator.evaluate("-2 * 3"), 1e-9)
        assertEquals(1.0, ArithmeticEvaluator.evaluate("7 % 3"), 1e-9)
    }

    @Test
    fun `unicode operators`() {
        assertEquals(8.0, ArithmeticEvaluator.evaluate("2 × 4"), 1e-9)
        assertEquals(4.0, ArithmeticEvaluator.evaluate("8 ÷ 2"), 1e-9)
    }

    @Test
    fun `rejects malformed input`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticEvaluator.evaluate("2 + + * 3")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticEvaluator.evaluate("(2 + 3")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArithmeticEvaluator.evaluate("drop table users")
        }
    }
}
