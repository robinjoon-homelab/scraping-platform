package com.example.template.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class ThymeleafSourceRulesTest {
    @ParameterizedTest
    @MethodSource("forbiddenSources")
    fun `reports the rule a forbidden source breaks`(
        source: String,
        rule: String,
    ) {
        assertThat(rulesOf("admin/x.html", LAYOUT_CALL + source)).containsExactly(rule)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "<script type=\"module\" th:src=\"@{/app/x.js}\"></script>",
            "<link rel=\"stylesheet\" th:href=\"@{/ui/tokens.css}\">",
            "<a href=\"https://x.test\">x</a>",
            "<p>[[\${x}]]</p>",
            "<p th:text=\"\${x}\">x</p>",
            "<form method=\"get\"></form>",
            "<form th:action=\"@{/x}\" method=\"post\"></form>",
            "<script data-th-src=\"@{/app/x.js}\"></script>",
            "<form data-th-action=\"@{/x}\" method=\"post\"></form>",
        ],
    )
    fun `accepts a source that follows the rules`(source: String) {
        assertThat(rulesOf("admin/x.html", LAYOUT_CALL + source)).isEmpty()
    }

    @Test
    fun `requires the layout head in a page template`() {
        assertThat(rulesOf("admin/x.html", "<html></html>")).containsExactly("missing-layout")
    }

    @Test
    fun `accepts a page template that uses the layout head`() {
        assertThat(rulesOf("admin/x.html", LAYOUT_CALL)).isEmpty()
    }

    @Test
    fun `accepts the layout head called through the data attribute spelling`() {
        assertThat(rulesOf("admin/x.html", "<head data-th-replace=\"~{ui/layout :: head('x')}\"></head>")).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["ui/x.html", "app/x.html"])
    fun `does not require the layout head in kit and app fragments`(path: String) {
        assertThat(rulesOf(path, "<html></html>")).isEmpty()
    }

    @Test
    fun `requires the layout head in an error page`() {
        assertThat(rulesOf("error/x.html", "<html></html>")).containsExactly("missing-layout")
    }

    @Test
    fun `reports every violation in a file with its template`() {
        val violations = ThymeleafSourceRules.check("admin/x.html", "<div style=\"color:red\" onclick=\"x()\"></div>")

        assertThat(violations.map { it.rule }).containsExactlyInAnyOrder("inline-style", "event-handler", "missing-layout")
        assertThat(violations.map { it.template }).containsOnly("admin/x.html")
    }

    private fun rulesOf(
        path: String,
        source: String,
    ): List<String> = ThymeleafSourceRules.check(path, source).map { it.rule }

    companion object {
        private const val LAYOUT_CALL = "<head th:replace=\"~{ui/layout :: head('x')}\"></head>"

        @JvmStatic
        fun forbiddenSources(): List<Arguments> =
            listOf(
                arguments("<div style=\"color:red\"></div>", "inline-style"),
                arguments("<div th:style=\"\${x}\"></div>", "inline-style"),
                arguments("<style>a{}</style>", "inline-style"),
                arguments("<script>alert(1)</script>", "inline-script"),
                arguments("<script src=\"/a.js\" th:inline=\"javascript\"></script>", "inline-script"),
                arguments("<a onclick=\"x()\">x</a>", "event-handler"),
                arguments("<a ONCLICK=\"x()\">x</a>", "event-handler"),
                arguments("<a th:onclick=\"\${x}\">x</a>", "event-handler"),
                arguments("<link rel=\"stylesheet\" href=\"https://cdn.test/x.css\">", "external-asset"),
                arguments("<script src=\"//cdn.test/x.js\"></script>", "external-asset"),
                arguments("<link th:href=\"@{https://cdn.test/x.css}\">", "external-asset"),
                arguments("<p th:utext=\"\${x}\">x</p>", "unescaped-output"),
                arguments("<p>[(\${x})]</p>", "unescaped-output"),
                arguments("<form method=\"post\"></form>", "post-without-th-action"),
                arguments("<form method=\"POST\" action=\"/x\"></form>", "post-without-th-action"),
                arguments("<p data-th-utext=\"\${x}\">x</p>", "unescaped-output"),
                arguments("<div data-th-style=\"\${x}\"></div>", "inline-style"),
                arguments("<a data-th-onclick=\"\${x}\">x</a>", "event-handler"),
                arguments("<script src=\"/a.js\" data-th-inline=\"javascript\"></script>", "inline-script"),
                arguments("<link data-th-href=\"@{https://cdn.test/x.css}\">", "external-asset"),
            )
    }
}
