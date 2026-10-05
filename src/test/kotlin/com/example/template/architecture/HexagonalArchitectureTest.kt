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
        ArchitectureRules("com.example.template").all().map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(productionClasses) }
        }

    @Test
    fun `production class scan is nonempty and excludes test fixtures`() {
        assertThat(productionClasses.map { it.name })
            .contains("com.example.template.TemplateApplication", "com.example.template.domain.Todo")
            .noneMatch { it.contains(".architecture.") }
    }

    companion object {
        private val productionPaths =
            requireNotNull(System.getProperty("architecture.productionClasses")) {
                "Run architecture tests with Gradle so all production output directories are supplied."
            }.split(File.pathSeparator).map(Path::of)

        private val productionClasses = ClassFileImporter().importPaths(productionPaths)
    }
}
