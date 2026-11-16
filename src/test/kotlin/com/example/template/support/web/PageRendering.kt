package com.example.template.support.web

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.spring6.view.ThymeleafViewResolver
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

private const val RENDERED_OUTPUT_PROPERTY = "ui.renderedOutput"

// Renders real templates through a real view resolver without an application context, like RestDocsWebTest does for JSON.
fun pageMockMvc(
    controller: Any,
    vararg advice: Any,
): MockMvc =
    MockMvcBuilders
        .standaloneSetup(controller)
        .setControllerAdvice(*advice)
        .setViewResolvers(thymeleafViewResolver())
        .build()

fun MockMvc.renderedPage(
    path: String,
    snapshot: String,
): String {
    val html =
        perform(get(path).accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(StandardCharsets.UTF_8)
    saveRenderedPage(snapshot, html)
    return html
}

// The uiHtmlCheck task validates these files, so a page rendered by any other means must be saved here too.
fun saveRenderedPage(
    snapshot: String,
    html: String,
) {
    val outputDirectory = checkNotNull(System.getProperty(RENDERED_OUTPUT_PROPERTY)) { "$RENDERED_OUTPUT_PROPERTY is not set." }
    Path
        .of(outputDirectory)
        .createDirectories()
        .resolve("$snapshot.html")
        .writeText(html)
}

private fun thymeleafViewResolver(): ThymeleafViewResolver {
    val templateResolver = ClassLoaderTemplateResolver()
    templateResolver.prefix = "templates/"
    templateResolver.suffix = ".html"
    templateResolver.templateMode = TemplateMode.HTML
    templateResolver.characterEncoding = StandardCharsets.UTF_8.name()
    val templateEngine = SpringTemplateEngine()
    templateEngine.setTemplateResolver(templateResolver)
    val viewResolver = ThymeleafViewResolver()
    viewResolver.templateEngine = templateEngine
    viewResolver.characterEncoding = StandardCharsets.UTF_8.name()
    return viewResolver
}
