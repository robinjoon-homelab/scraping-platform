package com.example.quality

import dev.detekt.api.Config
import dev.detekt.test.junit.KotlinCoreEnvironmentTest
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KotlinCoreEnvironmentTest
class NoMutableCollectionExposureTest(
    private val environment: KotlinEnvironmentContainer,
) {
    private val rule = NoMutableCollectionExposure(Config.empty)

    @Test
    fun `public list set and map properties are rejected`() {
        val code =
            """
            val names: MutableList<String> = mutableListOf()
            val tags: MutableSet<String> = mutableSetOf()
            val values: MutableMap<String, Int> = mutableMapOf()
            """.trimIndent()

        assertEquals(3, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `inferred property and function types are rejected`() {
        val code =
            """
            val names = mutableListOf("hello")
            fun tags() = mutableSetOf("hello")
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `nested and nullable mutable types are rejected`() {
        val code =
            """
            fun values(): List<MutableMap<String, Int>> = emptyList()
            fun names(): MutableList<String>? = null
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `type aliases and import aliases cannot hide mutable contracts`() {
        val code =
            """
            import kotlin.collections.MutableList as Names
            typealias Values = MutableMap<String, Int>
            fun names(): Names<String> = mutableListOf()
            fun values(): Values = mutableMapOf()
            """.trimIndent()

        assertEquals(3, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `mutable implementation types are rejected`() {
        val code =
            """
            class Names : ArrayList<String>()
            fun names() = Names()
            fun values() = ArrayList<String>()
            """.trimIndent()

        assertEquals(3, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `public function input and extension receiver are checked`() {
        val code =
            """
            fun consume(names: MutableList<String>) = names.size
            fun MutableSet<String>.nameCount() = size
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `public constructor input stays exposed despite private property`() {
        val code =
            """
            class Names(private val values: MutableList<String>)
            class Tags(values: MutableSet<String>)
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `public property stays exposed despite private constructor`() {
        val code =
            """
            class Names private constructor(val values: MutableList<String>)
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `protected contracts are exposed`() {
        val code =
            """
            open class Names {
                protected val values = mutableListOf<String>()
            }
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `private internal and local mutable implementation details are allowed`() {
        val code =
            """
            private val names = mutableListOf<String>()
            internal val tags = mutableSetOf<String>()
            private class Hidden(val names: MutableList<String>)
            internal class Internal(val names: MutableList<String>)
            class Visible private constructor(private val names: MutableList<String>) {
                private fun values() = mutableMapOf<String, Int>()
                fun count(): Int {
                    val local = mutableListOf<String>()
                    fun helper() = mutableSetOf<String>()
                    return local.size + helper().size
                }
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `read only contracts and Nothing return type are allowed`() {
        val code =
            """
            class Names(val values: List<String>)
            fun consume(values: List<Set<String>>): Map<String, Int> = emptyMap()
            fun fail() = error("failed")
            fun <T : Comparable<T>> identity(value: T): T = value
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `mutable type parameter upper bound is rejected`() {
        val code =
            """
            fun <T : MutableList<String>> identity(value: T): T = value
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `inherited nested mutable element contracts are rejected`() {
        val code =
            """
            interface Rows : List<MutableList<String>>
            fun rows(): Rows = error("unused")
            """.trimIndent()

        assertEquals(2, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `function type parameter names do not create variable symbols`() {
        val code =
            """
            fun execute(operation: (value: String) -> Unit) = operation("hello")
            fun consume(operation: (value: MutableList<String>) -> Unit) = operation(mutableListOf())
            """.trimIndent()

        assertEquals(1, rule.lintWithContext(environment, code).size)
    }

    @Test
    fun `nullable Nothing and anonymous object details are allowed`() {
        val code =
            """
            fun none() = null
            fun count(): Int {
                val local = object { val names = mutableListOf<String>() }
                return local.names.size
            }
            """.trimIndent()

        assertEquals(0, rule.lintWithContext(environment, code).size)
    }
}
