package com.example.template.support.persistence

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

// A source, a task of it and a running run of that task, inserted with SQL so that only the adapter under test takes part.
class SeededRun(
    val sourceId: UUID,
    val taskId: UUID,
    val runId: UUID,
)

// Passing an earlier seed adds another run of the same task and source; each run gets its own scheduled time.
fun MigratedH2Database.seedRun(sameTaskAs: SeededRun? = null): SeededRun {
    val sourceId = sameTaskAs?.sourceId ?: UUID.randomUUID()
    val taskId = sameTaskAs?.taskId ?: UUID.randomUUID()
    val runId = UUID.randomUUID()
    val time = "TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00'"
    val scheduled = "TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00' + INTERVAL '${runCounter.incrementAndGet()}' HOUR"
    if (sameTaskAs == null) seedTask(sourceId, taskId, time)
    execute(
        "INSERT INTO collection_runs (id, task_id, task_version, task_display_name, resource, parameters, every_seconds, anchor_at, " +
            "source_id, source_display_name, source_kind, source_base_url, source_created_at, scheduled_for, started_at, status) " +
            "VALUES ('$runId', '$taskId', 0, 'Prices', '/observations', '{}', 3600, $time, '$sourceId', 'Economy', 'HTTP_JSON', " +
            "'https://example.com', $time, $scheduled, $time, 'RUNNING')",
    )
    return SeededRun(sourceId, taskId, runId)
}

private val runCounter = AtomicInteger()

private fun MigratedH2Database.seedTask(
    sourceId: UUID,
    taskId: UUID,
    time: String,
) {
    execute(
        "INSERT INTO sources (id, display_name, kind, base_url, credential_ref, created_at) " +
            "VALUES ('$sourceId', 'Economy', 'HTTP_JSON', 'https://example.com', NULL, $time)",
    )
    execute(
        "INSERT INTO collection_tasks (id, display_name, source_id, resource, parameters, every_seconds, anchor_at, enabled, version, " +
            "created_at) VALUES ('$taskId', 'Prices', '$sourceId', '/observations', '{}', 3600, $time, FALSE, 0, $time)",
    )
}
