package com.example.template.collection.adapter.inbound.web.view

import com.example.template.collection.application.model.StoredCollectedRecord
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.source.Source
import com.example.template.collection.domain.task.CollectionTask
import java.util.UUID

data class SourceView(
    val id: UUID,
    val name: String,
    val kind: String,
    val baseUrl: String,
) {
    companion object {
        fun from(source: Source) = SourceView(source.id, source.definition.name, source.definition.kind.name, source.definition.baseUrl)
    }
}

data class PlanView(
    val id: UUID,
    val name: String,
    val enabled: Boolean,
    val version: Long,
    val nextRun: String,
) {
    companion object {
        fun from(task: CollectionTask) =
            PlanView(
                task.id,
                task.definition.name,
                task.enabled,
                task.version,
                task.nextRunAt?.toString() ?: "예약 없음",
            )
    }
}

data class RunView(
    val id: UUID,
    val status: String,
    val scheduled: String,
    val started: String,
    val finished: String,
    val error: String,
) {
    companion object {
        fun from(run: CollectionRun) =
            RunView(
                run.id,
                when (run.status.name) {
                    "PENDING" -> "대기"
                    "RUNNING" -> "실행 중"
                    "SUCCEEDED" -> "성공"
                    else -> "실패"
                },
                run.scheduledFor.toString(),
                run.startedAt?.toString() ?: "미시작",
                run.finishedAt?.toString() ?: "미완료",
                run.failureReason ?: "없음",
            )
    }
}

data class RecordView(
    val id: UUID,
    val key: String,
    val hash: String,
    val schema: String,
    val payload: String,
    val provenance: List<String>,
) {
    companion object {
        fun from(stored: StoredCollectedRecord): RecordView {
            val record = stored.record
            return RecordView(
                stored.id,
                record.identity.sourceRecordKey,
                record.identity.revisionHash,
                record.schemaVersion,
                record.payload,
                listOf(
                    "출처: ${record.provenance.sourceId}",
                    "실행: ${record.provenance.runId}",
                    "수집: ${record.provenance.collectedAt}",
                    "원천 수정: ${record.provenance.sourceUpdatedAt ?: "알 수 없음"}",
                ),
            )
        }
    }
}
