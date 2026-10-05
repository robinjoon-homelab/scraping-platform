package com.example.quality

import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class LogicalNestingDepthTest(
    private val environment: KotlinEnvironmentContainer,
) {
    private val rule = LogicalNestingDepth(Config.empty)

    @Test
    fun `two control flow levels are allowed inside arbitrary DSL wrappers`() {
        val code =
            """
            fun transaction(block: () -> Unit) = block()
            fun unknownDsl(block: () -> Unit) = block()
            fun operation() = transaction {
                unknownDsl {
                    if (true) {
                        for (item in 1..3) println(item)
                    }
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `third branch inside unknown DSL is rejected`() {
        val code =
            """
            fun transaction(block: () -> Unit) = block()
            fun unknownDsl(block: () -> Unit) = block()
            fun operation() = transaction {
                if (true) {
                    for (item in 1..3) {
                        unknownDsl {
                            if (item > 1) println(item)
                        }
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `map and flatMap bodies cannot hide deeply nested branches`() {
        val code =
            """
            fun operation() = (1..3).map { item ->
                if (item > 1) {
                    (1..item).flatMap { child ->
                        if (child > 1) {
                            when (child) {
                                2 -> emptyList()
                                else -> listOf(child)
                            }
                        } else emptyList()
                    }
                } else emptyList()
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `map and flatMap wrappers do not add depth`() {
        val code =
            """
            fun operation() = (1..3).map { item ->
                (1..item).flatMap { child ->
                    if (child > 1) {
                        if (item > 1) listOf(child) else emptyList()
                    } else emptyList()
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `else if ladder does not add depth`() {
        val code =
            """
            fun operation(value: Int) {
                if (value == 1) println(1)
                else if (value == 2) println(2)
                else if (value == 3) {
                    if (value > 0) println(3)
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `unbraced nested if is counted unlike else if`() {
        val code =
            """
            fun operation() {
                if (true) if (true) if (true) println(1)
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `while and do while count as loops`() {
        val code =
            """
            fun operation() {
                while (true) {
                    do {
                        if (true) break
                    } while (false)
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `try catch and finally wrappers do not add depth`() {
        val code =
            """
            fun operation() {
                if (true) {
                    try {
                        for (item in 1..3) println(item)
                    } catch (failure: IllegalStateException) {
                        for (item in 1..3) println(item)
                    } finally {
                        for (item in 1..3) println(item)
                    }
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `catch body still rejects three control flow levels`() {
        val code =
            """
            fun operation() {
                try {
                    println(1)
                } catch (failure: IllegalStateException) {
                    if (true) {
                        for (item in 1..3) {
                            if (item > 1) println(item)
                        }
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `local functions start with independent depth`() {
        val code =
            """
            fun operation() {
                if (true) {
                    for (item in 1..3) {
                        fun extract() {
                            if (item > 1) {
                                for (child in 1..item) println(child)
                            }
                        }
                        extract()
                    }
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `violations in local functions are checked independently`() {
        val code =
            """
            fun operation() {
                fun extract() {
                    if (true) {
                        for (item in 1..3) {
                            if (item > 1) println(item)
                        }
                    }
                }
                extract()
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `three nested collection forEach calls are rejected`() {
        val code =
            """
            fun operation() {
                (1..3).forEach {
                    (1..3).forEach {
                        (1..3).forEach { println(it) }
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `sequence forEach combines with branches and loops`() {
        val code =
            """
            fun operation() {
                (1..3).asSequence().forEach { item ->
                    if (item > 1) {
                        for (child in 1..item) println(child)
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `collection and sequence forEachIndexed count as loops`() {
        val code =
            """
            fun operation() {
                (1..3).forEachIndexed { outer, _ ->
                    (1..3).asSequence().forEachIndexed { inner, item ->
                        if (item > 1) println(outer + inner)
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `import aliases cannot hide collection forEach calls`() {
        val code =
            """
            import kotlin.collections.forEach as consumeEach
            fun operation() {
                (1..3).consumeEach {
                    (1..3).consumeEach {
                        (1..3).consumeEach { println(it) }
                    }
                }
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `custom methods named forEach do not count as collection loops`() {
        val code =
            """
            class Batch {
                fun forEach(block: () -> Unit) = block()
            }
            fun operation() {
                Batch().forEach {
                    if (true) {
                        for (item in 1..3) println(item)
                    }
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `configured depth is respected`() {
        val code =
            """
            fun operation() {
                if (true) {
                    for (item in 1..3) println(item)
                }
            }
            """.trimIndent()

        assertEquals(1, LogicalNestingDepth(TestConfig("allowedDepth" to 1)).lintWithContext(environment, code).size)
    }
}
