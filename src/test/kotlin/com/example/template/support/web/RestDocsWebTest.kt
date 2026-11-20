package com.example.template.support.web

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder

// Runs one real controller with its real advice on the Spring MVC defaults, which include Jackson with the Kotlin
// module and Bean Validation. No application context, service, or database takes part, so these tests check only
// the HTTP boundary: routing, binding, validation, serialization, status, headers, and error conversion.
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
    ): MockMvc =
        MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(advice)
            .apply<StandaloneMockMvcBuilder>(
                documentationConfiguration(restDocumentation)
                    .uris()
                    .withScheme("https")
                    .withHost("api.example.com")
                    .withPort(443),
            ).build()
}

fun documented(
    identifier: String,
    vararg snippets: Snippet,
): RestDocumentationResultHandler = document(identifier, preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), *snippets)

fun problemFields(): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("type")
            .type(JsonFieldType.STRING)
            .optional()
            .description("오류 유형 URI. 생략되면 RFC 9457 기본값 about:blank입니다."),
        fieldWithPath("title").description("오류 요약"),
        fieldWithPath("status").description("HTTP 상태 코드"),
        fieldWithPath("detail").description("오류의 구체적인 원인"),
        fieldWithPath("instance").description("오류가 발생한 요청 경로"),
    )
