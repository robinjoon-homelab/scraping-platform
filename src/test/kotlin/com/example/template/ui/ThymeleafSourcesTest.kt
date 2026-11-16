package com.example.template.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

class ThymeleafSourcesTest {
    private val templateRoot = Path.of("src/main/resources/templates")

    // Keyed by path relative to the template root, which is what the rules classify a template by.
    private val templates: Map<String, String> =
        templateRoot
            .walk()
            .filter { it.isRegularFile() && it.extension == "html" }
            .associate { it.relativeTo(templateRoot).invariantSeparatorsPathString to it.readText() }

    @Test
    fun `every template follows the source rules`() {
        val violations = templates.flatMap { (path, source) -> ThymeleafSourceRules.check(path, source) }

        // An empty scan would pass and silently switch this gate off.
        assertThat(templates).isNotEmpty
        assertThat(violations).isEmpty()
    }

    // Kit files are hash-locked, so an app URL in one would stop an existing app from keeping its own URLs.
    @Test
    fun `kit templates contain no application urls`() {
        val kitTemplates = templates.filterKeys { it.startsWith("ui/") || it.startsWith("error/") }

        assertThat(kitTemplates).isNotEmpty
        assertThat(kitTemplates.filterValues { "/admin" in it }.keys).isEmpty()
    }
}
