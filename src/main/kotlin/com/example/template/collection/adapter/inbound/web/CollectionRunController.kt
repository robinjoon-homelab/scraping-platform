package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class CollectionRunController(
    private val queryUseCase: CollectionRunQueryUseCase,
) {
    @GetMapping("/api/collection-tasks/{id}/runs")
    fun listRuns(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<CollectionRunResponse> = queryUseCase.listRuns(id, offset, limit).map(CollectionRunResponse::from)

    @GetMapping("/api/collection-runs/{id}")
    fun getRun(
        @PathVariable id: UUID,
    ): CollectionRunResponse = CollectionRunResponse.from(queryUseCase.getRun(id))

    @GetMapping("/api/collection-runs/{id}/records")
    fun listRecords(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<CollectedRecordSummaryResponse> = queryUseCase.listRecords(id, offset, limit).map(CollectedRecordSummaryResponse::from)

    @GetMapping("/api/collected-records/{id}")
    fun getRecord(
        @PathVariable id: UUID,
    ): CollectedRecordResponse = CollectedRecordResponse.from(queryUseCase.getRecord(id))
}
