package com.example.template.adapter.inbound.web

import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import javax.sql.DataSource

internal fun MockHttpServletRequestBuilder.authorized(): MockHttpServletRequestBuilder =
    header(HttpHeaders.AUTHORIZATION, "Bearer test-operator-token")

internal fun documented(
    identifier: String,
    vararg snippets: Snippet,
) = document(
    identifier,
    preprocessRequest(prettyPrint()),
    preprocessResponse(prettyPrint()),
    requestHeaders(headerWithName(HttpHeaders.AUTHORIZATION).description("운영자 API 토큰, Bearer 형식")),
    *snippets,
)

internal fun resourceIdParameter() = pathParameters(parameterWithName("id").description("리소스 UUID"))

internal fun paginationParameters() =
    queryParameters(
        parameterWithName("offset").optional().description("건너뛸 개수, 0 이상. 기본값 0"),
        parameterWithName("limit").optional().description("조회 개수, 1~100. 기본값 50"),
    )

internal fun sourceDefinitionFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}name").description("1~120자 이름. 저장 시 앞뒤 공백 제거"),
        fieldWithPath("${prefix}kind").description("수집 방식: HTTP_JSON, RSS_ATOM, HTML"),
        fieldWithPath("${prefix}baseUrl").description("최대 2048자 HTTP(S) 기본 URL. 사용자 정보, 쿼리, fragment 금지"),
        fieldWithPath("${prefix}credentialRef")
            .type(JsonFieldType.STRING)
            .optional()
            .description("자격 증명 참조 이름. 비밀값 자체를 입력하지 않음"),
    )

internal fun sourceFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}id").description("출처 UUID"),
        *sourceDefinitionFields("${prefix}definition."),
        fieldWithPath("${prefix}createdAt").description("생성 시각, ISO 8601 UTC"),
    )

internal fun taskDefinitionFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}name").description("1~120자 수집 작업 이름. 저장 시 앞뒤 공백 제거"),
        fieldWithPath("${prefix}sourceId").description("등록된 출처 UUID"),
        fieldWithPath("${prefix}target.resource").description("/로 시작하는 수집 경로. 쿼리는 parameters에 분리"),
        subsectionWithPath("${prefix}target.parameters")
            .type(JsonFieldType.OBJECT)
            .optional()
            .description("최대 50개의 문자열 쿼리 매개변수. 비밀값 금지"),
        fieldWithPath("${prefix}schedule.everySeconds").description("수집 주기, 60~31536000초"),
        fieldWithPath("${prefix}schedule.anchorAt").description("주기의 기준 시각, ISO 8601 UTC. 1970~9999년"),
    )

internal fun taskFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}id").description("수집 작업 UUID"),
        *taskDefinitionFields("${prefix}definition."),
        fieldWithPath("${prefix}enabled").description("수집 활성화 설정. 현재 버전은 실행기를 제공하지 않음"),
        fieldWithPath("${prefix}version").description("낙관적 잠금 버전. 생성 시 0, 변경마다 1 증가"),
        fieldWithPath("${prefix}createdAt").description("생성 시각, ISO 8601 UTC"),
    )

internal fun problemFields(): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("type").type(JsonFieldType.STRING).optional().description("오류 유형 URI, 기본값 about:blank"),
        fieldWithPath("title").description("오류 요약"),
        fieldWithPath("status").description("HTTP 상태 코드"),
        fieldWithPath("detail").description("오류의 구체적인 원인"),
        fieldWithPath("instance").description("오류가 발생한 요청 경로"),
    )

internal fun DataSource.clearConfiguration() {
    connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM collection_tasks")
            statement.executeUpdate("DELETE FROM sources")
        }
    }
}

internal fun DataSource.assertConfigurationCount(
    table: String,
    expected: Int,
) {
    require(table in setOf("sources", "collection_tasks"))
    connection.use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                assertThat(result.next()).isTrue()
                assertThat(result.getInt(1)).isEqualTo(expected)
            }
        }
    }
}

internal fun MockMvc.createSource(name: String = "Sample feed"): UUID {
    val location =
        perform(
            post("/api/sources")
                .authorized()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$name","kind":"HTTP_JSON","baseUrl":"https://example.com"}"""),
        ).andExpect(status().isCreated)
            .andReturn()
            .response
            .getHeader("Location")
    return UUID.fromString(checkNotNull(location).substringAfterLast('/'))
}
