package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionRunSlotTakenException
import com.example.template.collection.domain.run.CollectionRun
import com.example.template.collection.domain.run.CollectionRunStatus
import com.example.template.collection.domain.run.RunState
import com.example.template.collection.domain.run.RunTimeline
import com.example.template.collection.domain.run.SourceSnapshot
import com.example.template.collection.domain.run.TaskSnapshot
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedCollectionRunRepository(
    private val jsonMapper: JsonMapper,
) : CollectionRunRepository {
    override fun insert(run: CollectionRun): CollectionRun {
        try {
            CollectionRunsTable.insert {
                it[id] = run.id
                writeRun(it, run)
            }
        } catch (exception: ExposedSQLException) {
            if (exception.isSlotConflict()) throw CollectionRunSlotTakenException(run.task.taskId, run.scheduledFor, exception)
            throw exception
        }
        return requireNotNull(findById(run.id))
    }

    override fun findById(id: UUID): CollectionRun? =
        CollectionRunsTable
            .selectAll()
            .where { CollectionRunsTable.id eq id }
            .singleOrNull()
            ?.toRun()

    override fun existsForSlot(
        taskId: UUID,
        scheduledFor: Instant,
    ): Boolean =
        !CollectionRunsTable
            .selectAll()
            .where { (CollectionRunsTable.taskId eq taskId) and (CollectionRunsTable.scheduledFor eq scheduledFor.toStoredTime()) }
            .empty()

    override fun findPending(
        now: Instant,
        limit: Int,
    ): List<CollectionRun> =
        CollectionRunsTable
            .selectAll()
            .where {
                (CollectionRunsTable.status eq CollectionRunStatus.PENDING.name) and
                    (CollectionRunsTable.scheduledFor lessEq now.toStoredTime())
            }.orderBy(CollectionRunsTable.scheduledFor to SortOrder.ASC, CollectionRunsTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toRun() }

    override fun findExpiredLeases(
        now: Instant,
        limit: Int,
    ): List<CollectionRun> =
        CollectionRunsTable
            .selectAll()
            .where {
                (CollectionRunsTable.status eq CollectionRunStatus.RUNNING.name) and
                    (CollectionRunsTable.leaseUntil.isNull() or (CollectionRunsTable.leaseUntil less now.toStoredTime()))
            }.orderBy(CollectionRunsTable.leaseUntil to SortOrder.ASC_NULLS_FIRST, CollectionRunsTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toRun() }

    override fun updateStarted(
        run: CollectionRun,
        leaseUntil: Instant,
    ): CollectionRun? {
        require(run.status == CollectionRunStatus.RUNNING) { "only a running run can take a lease" }
        return updateWhileStatus(run, CollectionRunStatus.PENDING) {
            it[CollectionRunsTable.leaseUntil] = leaseUntil.toStoredTime()
        }
    }

    override fun update(
        run: CollectionRun,
        expectedStatus: CollectionRunStatus,
    ): CollectionRun? {
        if (expectedStatus in finalStatuses) return null
        return updateWhileStatus(run, expectedStatus) {
            if (run.status in finalStatuses) it[CollectionRunsTable.leaseUntil] = null
        }
    }

    private fun updateWhileStatus(
        run: CollectionRun,
        expectedStatus: CollectionRunStatus,
        writeLease: (UpdateBuilder<*>) -> Unit,
    ): CollectionRun? {
        val updated =
            CollectionRunsTable.update({
                (CollectionRunsTable.id eq run.id) and (CollectionRunsTable.status eq expectedStatus.name)
            }) {
                writeRun(it, run)
                writeLease(it)
            }
        if (updated == 0) return null
        return findById(run.id)
    }

    private fun writeRun(
        statement: UpdateBuilder<*>,
        run: CollectionRun,
    ) {
        val task = run.task
        val definition = task.definition
        val source = task.source
        statement[CollectionRunsTable.taskId] = task.taskId
        statement[CollectionRunsTable.taskVersion] = task.version
        statement[CollectionRunsTable.taskName] = definition.name
        statement[CollectionRunsTable.resource] = definition.target.resource
        statement[CollectionRunsTable.parameters] = jsonMapper.writeValueAsString(definition.target.parameters)
        statement[CollectionRunsTable.everySeconds] = definition.schedule.everySeconds
        statement[CollectionRunsTable.anchorAt] = definition.schedule.anchorAt.toStoredTime()
        statement[CollectionRunsTable.sourceId] = source.id
        statement[CollectionRunsTable.sourceName] = source.definition.name
        statement[CollectionRunsTable.sourceKind] = source.definition.kind.name
        statement[CollectionRunsTable.sourceBaseUrl] = source.definition.baseUrl
        statement[CollectionRunsTable.sourceCredentialRef] = source.definition.credentialRef
        statement[CollectionRunsTable.sourceCreatedAt] = source.createdAt.toStoredTime()
        statement[CollectionRunsTable.scheduledFor] = run.scheduledFor.toStoredTime()
        statement[CollectionRunsTable.startedAt] = run.startedAt?.toStoredTime()
        statement[CollectionRunsTable.finishedAt] = run.finishedAt?.toStoredTime()
        statement[CollectionRunsTable.status] = run.status.name
        statement[CollectionRunsTable.failureReason] = run.failureReason
    }

    private fun ResultRow.toRun(): CollectionRun =
        CollectionRun.restore(
            id = this[CollectionRunsTable.id],
            task = toTaskSnapshot(),
            timeline =
                RunTimeline(
                    scheduledFor = this[CollectionRunsTable.scheduledFor].toInstant(),
                    startedAt = this[CollectionRunsTable.startedAt]?.toInstant(),
                    finishedAt = this[CollectionRunsTable.finishedAt]?.toInstant(),
                ),
            state = RunState(CollectionRunStatus.valueOf(this[CollectionRunsTable.status]), this[CollectionRunsTable.failureReason]),
        )

    private fun ResultRow.toTaskSnapshot(): TaskSnapshot =
        TaskSnapshot(
            taskId = this[CollectionRunsTable.taskId],
            version = this[CollectionRunsTable.taskVersion],
            definition = toTaskDefinition(),
            source =
                SourceSnapshot(
                    id = this[CollectionRunsTable.sourceId],
                    definition =
                        SourceDefinition(
                            name = this[CollectionRunsTable.sourceName],
                            kind = SourceKind.valueOf(this[CollectionRunsTable.sourceKind]),
                            baseUrl = this[CollectionRunsTable.sourceBaseUrl],
                            credentialRef = this[CollectionRunsTable.sourceCredentialRef],
                        ),
                    createdAt = this[CollectionRunsTable.sourceCreatedAt].toInstant(),
                ),
        )

    private fun ResultRow.toTaskDefinition(): TaskDefinition =
        TaskDefinition(
            name = this[CollectionRunsTable.taskName],
            sourceId = this[CollectionRunsTable.sourceId],
            target =
                CollectionTarget(
                    resource = this[CollectionRunsTable.resource],
                    parameters = jsonMapper.readValue(this[CollectionRunsTable.parameters], parameterType),
                ),
            schedule =
                IntervalSchedule(
                    everySeconds = this[CollectionRunsTable.everySeconds],
                    anchorAt = this[CollectionRunsTable.anchorAt].toInstant(),
                ),
        )

    // PostgreSQL and H2 both report unique violations as SQLSTATE 23505 and name the violated constraint in the message.
    private fun ExposedSQLException.isSlotConflict(): Boolean =
        sqlState == UNIQUE_VIOLATION && message.orEmpty().contains(SLOT_CONSTRAINT, ignoreCase = true)

    private fun Instant.toStoredTime(): OffsetDateTime = truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val SLOT_CONSTRAINT = "collection_runs_task_slot_unique"
        val finalStatuses = setOf(CollectionRunStatus.SUCCEEDED, CollectionRunStatus.FAILED)
        val parameterType = object : TypeReference<Map<String, String>>() {}
    }
}
