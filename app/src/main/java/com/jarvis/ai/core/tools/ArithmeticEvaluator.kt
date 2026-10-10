package com.jarvis.ai.core.tools

import kotlin.math.pow

/**
 * Minimal recursive-descent arithmetic evaluator. No third-party parser, no
 * eval() tricks — a deliberately boring, safe calculator.
 *
 * Grammar: expression -> term (('+'|'-') term)*
 *          term       -> factor (('*'|'/'|'%') factor)*
 *          factor     -> unary ('^' factor)?
 *          unary      -> ('-')? primary
 *          primary    -> NUMBER | '(' expression ')'
 */
object ArithmeticEvaluator {

    fun evaluate(expression: String): Double {
        val tokens = tokenize(expression)
        val parser = Parser(tokens)
        val result = parser.parseExpression()
        parser.expectEnd()
        return result
    }

    private fun tokenize(input: String): List<String> {
        val cleaned = input
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < cleaned.length && (cleaned[i].isDigit() || cleaned[i] == '.')) {
                        sb.append(cleaned[i])
                        i++
                    }
                    tokens.add(sb.toString())
                }
                c in "+-*/%^()" -> {
                    tokens.add(c.toString())
                    i++
                }
                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }
        return tokens
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        fun parseExpression(): Double {
            var value = parseTerm()
            while (peek() == "+" || peek() == "-") {
                val op = next()
                val rhs = parseTerm()
                value = if (op == "+") value + rhs else value - rhs
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (peek() == "*" || peek() == "/" || peek() == "%") {
                val op = next()
                val rhs = parseFactor()
                value = when (op) {
                    "*" -> value * rhs
                    "/" -> value / rhs
                    else -> value % rhs
                }
            }
            return value
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            if (peek() == "^") {
                next()
                val exponent = parseFactor() // right-associative
                return base.pow(exponent)
            }
            return base
        }

        private fun parseUnary(): Double {
            if (peek() == "-") {
                next()
                return -parseUnary()
            }
            if (peek() == "+") {
                next()
                return parseUnary()
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            val token = next()
            return when {
                token == "(" -> {
                    val value = parseExpression()
                    expect(")")
                    value
                }
                token.toDoubleOrNull() != null -> token.toDouble()
                else -> throw IllegalArgumentException("Unexpected token: $token")
            }
        }

        private fun peek(): String? = tokens.getOrNull(pos)

        private fun next(): String =
            tokens.getOrNull(pos)?.also { pos++ }
                ?: throw IllegalArgumentException("Unexpected end of expression")

        private fun expect(expected: String) {
            val token = next()
            if (token != expected) {
                throw IllegalArgumentException("Expected $expected but found $token")
            }
        }

        fun expectEnd() {
            if (pos < tokens.size) {
                throw IllegalArgumentException("Unexpected token: ${tokens[pos]}")
            }
        }
    }
}
