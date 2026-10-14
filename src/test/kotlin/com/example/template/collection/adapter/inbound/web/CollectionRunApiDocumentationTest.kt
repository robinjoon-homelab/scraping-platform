package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.model.CollectedRecordSummary
import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.application.port.input.CollectedRecordNotFoundException
import com.example.template.collection.application.port.input.CollectionRunNotFoundException
import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.support.web.RestDocsWebTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

// Paging rules, ordering and lookups belong to the service and adapter tests; this test checks how the endpoints turn
// use case results and failures into HTTP responses.
class CollectionRunApiDocumentationTest : RestDocsWebTest() {
    private val taskId: UUID = UUID.fromString("4e67ebda-dfbd-47d5-9373-ab9a97e7675a")
    private val sourceId: UUID = UUID.fromString("b1e38ed1-1d8a-4078-9322-a17fa7e30ec8")
    private val runId: UUID = UUID.fromString("5c0a7a37-7c1d-4a39-9d58-0d5b4f7c0a11")
    private val failedRunId: UUID = UUID.fromString("9d3a2f61-6b8e-4f0c-8d47-2a1b3c4d5e6f")
    private val recordId: UUID = UUID.fromString("c7d1e2f3-0a4b-4c5d-9e6f-7a8b9c0d1e2f")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val scheduledFor: Instant = Instant.parse("2026-10-05T00:00:00Z")
    private val startedAt: Instant = Instant.parse("2026-10-05T00:00:02.5Z")
    private val finishedAt: Instant = Instant.parse("2026-10-05T00:00:09Z")
    private val collectedAt: Instant = Instant.parse("2026-10-05T00:00:05.123456Z")
    private val revisionHash = "a".repeat(64)
    private val queryUseCase = mockk<CollectionRunQueryUseCase>()
    private val mockMvc = restDocsMockMvc(CollectionRunController(queryUseCase), ApiExceptionHandler(), operatorTokenFilter())

    @Test
    fun `runs of a task are listed with the requested page`() {
        every { queryUseCase.listRuns(taskId, 1, 2) } returns listOf(succeededRun(), failedRun())

        mockMvc
            .perform(get("/api/collection-tasks/{id}/runs", taskId).authorized().queryParam("offset", "1").queryParam("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(content().json("[${succeededRunJson()},${failedRunJson()}]", JsonCompareMode.STRICT))
            .andDo(documented("runs-list", resourceIdParameter(), paginationParameters(), responseFields(*runFields("[]."))))
    }

    @Test
    fun `a run is returned without lease details`() {
        every { queryUseCase.getRun(runId) } returns succeededRun()

        mockMvc
            .perform(get("/api/collection-runs/{id}", runId).authorized())
            .andExpect(status().isOk)
            .andExpect(content().json(succeededRunJson(), JsonCompareMode.STRICT))
            .andDo(documented("runs-get", resourceIdParameter(), responseFields(*runFields())))
    }

    @Test
    fun `run records are listed without payload`() {
        every { queryUseCase.listRecords(runId, 0, 50) } returns
            listOf(CollectedRecordSummary(recordId, RecordIdentity("/prices?page=1", revisionHash), "http-json/1", collectedAt, null, 11))

        mockMvc
            .perform(get("/api/collection-runs/{id}/records", runId).authorized())
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    [{"id":"$recordId","identity":{"sourceRecordKey":"/prices?page=1","revisionHash":"$revisionHash"},
                    "schemaVersion":"http-json/1","collectedAt":"$collectedAt","sourceUpdatedAt":null,"payloadBytes":11}]
                    """.trimIndent(),
                    JsonCompareMode.STRICT,
                ),
            ).andDo(
                documented("run-records-list", resourceIdParameter(), paginationParameters(), responseFields(*recordSummaryFields("[]."))),
            )
    }

    @Test
    fun `a record is returned with its payload`() {
        val updatedAt = Instant.parse("2026-10-04T23:59:00Z")
        every { queryUseCase.getRecord(recordId) } returns
            StoredCollectedRecord(
                recordId,
                CollectedRecord(
                    RecordIdentity("/prices?page=1", revisionHash),
                    "http-json/1",
                    "{\"price\":1}",
                    RecordProvenance(runId, sourceId, collectedAt, updatedAt),
                ),
            )

        mockMvc
            .perform(get("/api/collected-records/{id}", recordId).authorized())
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {"id":"$recordId","identity":{"sourceRecordKey":"/prices?page=1","revisionHash":"$revisionHash"},
                    "schemaVersion":"http-json/1",
                    "provenance":{"runId":"$runId","sourceId":"$sourceId","collectedAt":"$collectedAt","sourceUpdatedAt":"$updatedAt"},
                    "payload":"{\"price\":1}"}
                    """.trimIndent(),
                    JsonCompareMode.STRICT,
                ),
            ).andDo(documented("records-get", resourceIdParameter(), responseFields(*recordFields())))
    }

    @Test
    fun `missing resources return not found`() {
        every { queryUseCase.listRuns(missingId, 0, 50) } throws CollectionTaskNotFoundException(missingId)
        every { queryUseCase.getRun(missingId) } throws CollectionRunNotFoundException(missingId)
        every { queryUseCase.listRecords(missingId, 0, 50) } throws CollectionRunNotFoundException(missingId)
        every { queryUseCase.getRecord(missingId) } throws CollectedRecordNotFoundException(missingId)

        mapOf(
            "/api/collection-tasks/$missingId/runs" to "Collection task not found",
            "/api/collection-runs/$missingId" to "Collection run not found",
            "/api/collection-runs/$missingId/records" to "Collection run not found",
            "/api/collected-records/$missingId" to "Collected record not found",
        ).forEach { (path, title) ->
            mockMvc
                .perform(get(path).authorized())
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.instance").value(path))
        }
    }

    @Test
    fun `rejected pagination and malformed ids are bad requests`() {
        every { queryUseCase.listRuns(taskId, 0, 101) } throws InvalidCollectionConfigurationException("Limit must be between 1 and 100")
        every { queryUseCase.listRecords(runId, -1, 50) } throws InvalidCollectionConfigurationException("Offset must not be negative")

        mockMvc.perform(get("/api/collection-tasks/{id}/runs?limit=101", taskId).authorized()).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/collection-runs/{id}/records?offset=-1", runId).authorized()).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/collection-tasks/{id}/runs?offset=invalid", taskId).authorized()).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/collection-runs/{id}", "invalid-id").authorized()).andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/collected-records/{id}", "invalid-id").authorized()).andExpect(status().isBadRequest)
    }

    @Test
    fun `requests without the operator token are rejected`() {
        mockMvc.perform(get("/api/collection-runs/{id}", runId)).andExpect(status().isUnauthorized)
    }

    private fun snapshot() =
        TaskSnapshot(
            taskId = taskId,
            version = 3,
            definition =
                TaskDefinition(
                    "Market headlines",
                    sourceId,
                    CollectionTarget("/articles"),
                    IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00Z")),
                ),
            source =
                SourceSnapshot(
                    sourceId,
                    SourceDefinition("Economy", SourceKind.HTTP_JSON, "https://example.com", "api-key.v1"),
                    Instant.parse("2026-01-01T00:00:00Z"),
                ),
        )

    private fun succeededRun() = CollectionRun.pending(runId, snapshot(), scheduledFor).start(startedAt).succeed(finishedAt)

    private fun failedRun() =
        CollectionRun
            .pending(
                failedRunId,
                snapshot(),
                scheduledFor.minusSeconds(3600),
            ).start(startedAt.minusSeconds(3600))
            .fail(finishedAt.minusSeconds(3600), "Connector timed out")

    private fun succeededRunJson() = runJson(runId, "SUCCEEDED", Times(scheduledFor, startedAt, finishedAt), null)

    private fun failedRunJson() =
        runJson(
            failedRunId,
            "FAILED",
            Times(scheduledFor.minusSeconds(3600), startedAt.minusSeconds(3600), finishedAt.minusSeconds(3600)),
            "\"Connector timed out\"",
        )

    private class Times(
        val scheduledFor: Instant,
        val startedAt: Instant,
        val finishedAt: Instant,
    )

    private fun runJson(
        id: UUID,
        status: String,
        times: Times,
        failureReason: String?,
    ) = """
        {"id":"$id","task":{"id":"$taskId","version":3,"sourceId":"$sourceId"},"status":"$status","failureReason":$failureReason,
        "times":{"scheduledFor":"${times.scheduledFor}","startedAt":"${times.startedAt}","finishedAt":"${times.finishedAt}"}}
        """.trimIndent()
}
