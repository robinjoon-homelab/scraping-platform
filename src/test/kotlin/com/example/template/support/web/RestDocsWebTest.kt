package com.example.template.support.web

import jakarta.servlet.Filter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

// Runs one real controller with its real advice and the given web adapter filters on the Spring MVC defaults, which
// include Jackson with the Kotlin module and Bean Validation. No application context, service, or database takes
// part, so these tests check only the HTTP boundary: routing, binding, validation, serialization, status, headers,
// and error conversion.
abstract class RestDocsWebTest {
    // Managed by hand rather than by RestDocumentationExtension, so subclasses can build MockMvc in a val.
    private val restDocumentation = ManualRestDocumentation()

    @BeforeEach
    fun startDocumentation(testInfo: TestInfo) {
        restDocumentation.beforeTest(javaClass, testInfo.testMethod.orElseThrow().name)
    }

    @AfterEach
    fun finishDocumentation() {
        restDocumentation.afterTest()
    }

    // The URI settings keep the published host in the generated snippets.
    protected fun restDocsMockMvc(
        controller: Any,
        advice: Any,
        vararg filters: Filter,
    ): MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(advice)
            .addFilters<StandaloneMockMvcBuilder>(*filters)
            // The server forces UTF-8 request decoding, so documented JSON requests show that charset.
            .defaultRequest<StandaloneMockMvcBuilder>(get("/").characterEncoding(Charsets.UTF_8))
            .apply<StandaloneMockMvcBuilder>(
                documentationConfiguration(restDocumentation)
                    .uris()
                    .withScheme("https")
                    .withHost("api.example.com")
                    .withPort(443),
            ).build()
}
