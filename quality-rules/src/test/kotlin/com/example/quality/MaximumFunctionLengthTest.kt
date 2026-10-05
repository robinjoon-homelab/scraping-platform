package com.example.quality

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaximumFunctionLengthTest {
    private val rule = MaximumFunctionLength(Config.empty)

    @Test
    fun `80 body lines including braces are allowed`() {
        assertEquals(0, rule.lint(functionWithStatements(78)).size)
    }

    @Test
    fun `81 body lines are rejected despite LongMethod suppression`() {
        val code = "@Suppress(\"LongMethod\")\n" + functionWithStatements(79)

        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun `blank lines and comments do not count`() {
        val code = functionWithStatements(78).replace("consume()", "// explain why\n\nconsume()")

        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun `local function lines are included in the absolute cap`() {
        val code =
            """
            fun outer() {
                fun inner() {
                    ${List(77) { "consume()" }.joinToString("\n")}
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lint(code).size)
    }

    private fun functionWithStatements(count: Int): String = "fun operation() {\n" + List(count) { "consume()" }.joinToString("\n") + "\n}"
}
