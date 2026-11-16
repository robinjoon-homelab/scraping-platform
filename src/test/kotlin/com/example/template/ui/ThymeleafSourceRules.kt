package com.example.template.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

data class TemplateViolation(
    val template: String,
    val rule: String,
    val detail: String,
)

object ThymeleafSourceRules {
    private const val UNESCAPED_INLINE = "[("
    private const val DATA_ATTRIBUTE_PREFIX = "data-th-"
    private val layoutHeadCall = Regex("""ui/layout\s*::\s*head\b""")
    private val layoutExemptDirectories = listOf("ui/", "app/")
    private val assetElements = setOf("link", "script")
    private val assetAddressAttributes = listOf("href", "src", "th:href", "th:src")

    // The leading characters skip the link expression and literal wrappers Thymeleaf allows around an address.
    private val externalAddress = Regex("""^[\s@{'|]*(https?:|//)""", RegexOption.IGNORE_CASE)

    // jsoup lower-cases attribute names, so every check below also covers mixed-case spellings.
    fun check(
        relativePath: String,
        source: String,
    ): List<TemplateViolation> {
        val elements = Jsoup.parse(source).allElements
        elements.forEach(::normalizeDataAttributes)
        return mapOf(
            "inline-style" to inlineStyles(elements),
            "inline-script" to inlineScripts(elements),
            "event-handler" to eventHandlers(elements),
            "external-asset" to externalAssets(elements),
            "unescaped-output" to unescapedOutput(elements, source),
            "post-without-th-action" to postsWithoutThAction(elements),
            "missing-layout" to missingLayout(relativePath, elements),
        ).flatMap { (rule, details) -> details.map { TemplateViolation(relativePath, rule, it) } }
    }

    // Thymeleaf treats data-th-name exactly like th:name, so renaming here lets every rule know only the th: spelling.
    private fun normalizeDataAttributes(element: Element) {
        element
            .attributes()
            .filter { it.key.startsWith(DATA_ATTRIBUTE_PREFIX) }
            .forEach { it.setKey("th:" + it.key.removePrefix(DATA_ATTRIBUTE_PREFIX)) }
    }

    private fun inlineStyles(elements: Elements): List<String> =
        elements.filter { it.normalName() == "style" || it.hasAttr("style") || it.hasAttr("th:style") }.map(::describe)

    private fun inlineScripts(elements: Elements): List<String> =
        elements.filter { it.hasAttr("th:inline") || isScriptWithoutSource(it) }.map(::describe)

    private fun isScriptWithoutSource(element: Element): Boolean =
        element.normalName() == "script" && !element.hasAttr("src") && !element.hasAttr("th:src")

    private fun eventHandlers(elements: Elements): List<String> =
        elements.filter { element -> element.attributes().any { isEventHandler(it.key) } }.map(::describe)

    private fun isEventHandler(attributeName: String): Boolean = attributeName.removePrefix("th:").startsWith("on")

    private fun externalAssets(elements: Elements): List<String> =
        elements.filter { it.normalName() in assetElements && hasExternalAddress(it) }.map(::describe)

    private fun hasExternalAddress(element: Element): Boolean =
        assetAddressAttributes.any { externalAddress.containsMatchIn(element.attr(it)) }

    private fun unescapedOutput(
        elements: Elements,
        source: String,
    ): List<String> {
        val inlineOutput = if (UNESCAPED_INLINE in source) listOf("inline expression $UNESCAPED_INLINE...)]") else emptyList()
        return elements.filter { it.hasAttr("th:utext") }.map(::describe) + inlineOutput
    }

    private fun postsWithoutThAction(elements: Elements): List<String> =
        elements
            .filter { it.normalName() == "form" && it.attr("method").equals("post", ignoreCase = true) && !it.hasAttr("th:action") }
            .map(::describe)

    private fun missingLayout(
        relativePath: String,
        elements: Elements,
    ): List<String> {
        val exempt = layoutExemptDirectories.any(relativePath::startsWith)
        val usesLayoutHead = elements.any { element -> element.attributes().any { layoutHeadCall.containsMatchIn(it.value) } }
        return if (exempt || usesLayoutHead) emptyList() else listOf("ui/layout :: head is not used")
    }

    private fun describe(element: Element): String = element.shallowClone().outerHtml()
}
