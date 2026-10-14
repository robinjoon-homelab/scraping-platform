package com.example.template.collection.adapter.inbound.web

import org.springframework.http.HttpHeaders
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder

internal const val OPERATOR_TOKEN = "test-operator-token"

// The real token filter runs in front of the controller, so the documented requests and responses keep the
// authentication header contract.
internal fun operatorTokenFilter() = OperatorTokenFilter(OPERATOR_TOKEN)

internal fun MockHttpServletRequestBuilder.authorized(): MockHttpServletRequestBuilder =
    header(HttpHeaders.AUTHORIZATION, "Bearer $OPERATOR_TOKEN")

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
        fieldWithPath("${prefix}enabled").description("수집 활성화 설정. true면 다음 슬롯부터 주기 실행"),
        fieldWithPath("${prefix}version").description("낙관적 잠금 버전. 생성 시 0, 변경마다 1 증가"),
        fieldWithPath("${prefix}createdAt").description("생성 시각, ISO 8601 UTC"),
        fieldWithPath("${prefix}nextRunAt")
            .type(JsonFieldType.STRING)
            .optional()
            .description("다음 실행 예정 시각, ISO 8601 UTC. 비활성 계획은 null"),
    )

internal fun runFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}id").description("실행 UUID"),
        fieldWithPath("${prefix}task.id").description("실행한 수집 작업 UUID"),
        fieldWithPath("${prefix}task.version").description("실행 시점의 수집 작업 버전"),
        fieldWithPath("${prefix}task.sourceId").description("실행 시점의 출처 UUID"),
        fieldWithPath("${prefix}status").description("실행 상태: PENDING, RUNNING, SUCCEEDED, FAILED"),
        fieldWithPath("${prefix}failureReason").type(JsonFieldType.STRING).optional().description("실패 사유. FAILED가 아니면 null"),
        fieldWithPath("${prefix}times.scheduledFor").description("실행 예정 시각, ISO 8601 UTC"),
        fieldWithPath("${prefix}times.startedAt").type(JsonFieldType.STRING).optional().description("시작 시각, ISO 8601 UTC. 시작 전 null"),
        fieldWithPath("${prefix}times.finishedAt").type(JsonFieldType.STRING).optional().description("종료 시각, ISO 8601 UTC. 종료 전 null"),
    )

internal fun recordSummaryFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}id").description("레코드 UUID"),
        *recordIdentityFields(prefix),
        fieldWithPath("${prefix}schemaVersion").description("레코드 스키마 버전"),
        fieldWithPath("${prefix}collectedAt").description("수집 시각, ISO 8601 UTC"),
        fieldWithPath("${prefix}sourceUpdatedAt")
            .type(JsonFieldType.STRING)
            .optional()
            .description("출처가 알린 수정 시각, ISO 8601 UTC. 없으면 null"),
        fieldWithPath("${prefix}payloadBytes").description("payload의 UTF-8 바이트 수. 목록은 payload 자체를 포함하지 않음"),
    )

internal fun recordFields(prefix: String = ""): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}id").description("레코드 UUID"),
        *recordIdentityFields(prefix),
        fieldWithPath("${prefix}schemaVersion").description("레코드 스키마 버전"),
        fieldWithPath("${prefix}provenance.runId").description("이 내용 버전을 처음 관측해 저장한 실행 UUID"),
        fieldWithPath("${prefix}provenance.sourceId").description("출처 UUID"),
        fieldWithPath("${prefix}provenance.collectedAt").description("수집 시각, ISO 8601 UTC"),
        fieldWithPath("${prefix}provenance.sourceUpdatedAt")
            .type(JsonFieldType.STRING)
            .optional()
            .description("출처가 알린 수정 시각, ISO 8601 UTC. 없으면 null"),
        fieldWithPath("${prefix}payload").description("수집한 원문 payload. 최대 1,000,000자"),
    )

private fun recordIdentityFields(prefix: String): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("${prefix}identity.sourceRecordKey").description("출처 안에서 레코드를 구분하는 키"),
        fieldWithPath("${prefix}identity.revisionHash").description("내용 버전을 나타내는 SHA-256 소문자 16진수 64자"),
    )

internal fun problemFields(): Array<FieldDescriptor> =
    arrayOf(
        fieldWithPath("type").type(JsonFieldType.STRING).optional().description("오류 유형 URI, 기본값 about:blank"),
        fieldWithPath("title").description("오류 요약"),
        fieldWithPath("status").description("HTTP 상태 코드"),
        fieldWithPath("detail").description("오류의 구체적인 원인"),
        fieldWithPath("instance").description("오류가 발생한 요청 경로"),
    )
