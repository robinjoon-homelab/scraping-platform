package com.example.template.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.nio.file.Path

class HexagonalArchitectureTest {
    @TestFactory
    fun `production code follows every architecture rule`(): List<DynamicTest> =
        (ArchitectureRules(ROOT).all() + dddRules.all() + contextRules.all()).map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(productionClasses) }
        }

    @Test
    fun `production class scan is nonempty and excludes test fixtures`() {
        assertThat(productionClasses.map { it.name })
            .contains("$ROOT.TemplateApplication")
            .containsAll(aggregateRoots)
            .doesNotContain(
                ArchitectureRules::class.java.name,
                DddArchitectureRules::class.java.name,
                BoundedContextArchitectureRules::class.java.name,
                HexagonalArchitectureTest::class.java.name,
            ).noneMatch { it.startsWith("architecturefixtures.") }
    }

    @Test
    fun `every production aggregate matches the declared context inventory`() {
        assertThat(productionClasses.filter(dddRules::isAggregateRoot).map { it.name })
            .containsExactlyInAnyOrderElementsOf(aggregateRoots)
    }

    // Exact feature coverage also catches unregistered contexts and accidental nested feature paths.
    @Test
    fun `every business feature is one declared context`() {
        val features =
            productionClasses
                .mapNotNull { packages.location(it.packageName)?.feature }
                .filter { it.isNotEmpty() }
                .map { it.joinToString(".") }
                .toSet()

        assertThat(features).containsExactlyInAnyOrderElementsOf(contextAggregates.keys)
    }

    @Test
    fun `shared domain contains only the technical aggregate marker`() {
        val sharedDomain =
            productionClasses.filter {
                val location = packages.location(it.packageName)
                location?.layer == ArchitectureLayer.DOMAIN && location.feature.isEmpty()
            }

        assertThat(sharedDomain.map { it.name }).containsExactly("$ROOT.domain.AggregateRoot")
    }

    companion object {
        private const val ROOT = "com.example.template"
        private val contextAggregates =
            mapOf(
                "todo" to setOf("todo.Todo"),
                "ordering" to setOf("order.Order", "offer.Offer"),
                "inventory" to setOf("stock.StockItem"),
            )
        private val aggregateRoots =
            contextAggregates.flatMap { (context, roots) -> roots.map { "$ROOT.$context.domain.$it" } }
        private val packages = ArchitecturePackages(ROOT)
        private val dddRules = DddArchitectureRules(ROOT)
        private val contextRules = BoundedContextArchitectureRules(ROOT, contextAggregates.keys)

        private val productionPaths =
            requireNotNull(System.getProperty("architecture.productionClasses")) {
                "Run architecture tests with Gradle so all production output directories are supplied."
            }.split(File.pathSeparator).map(Path::of)

        private val productionClasses = ClassFileImporter().importPaths(productionPaths)
    }
}
