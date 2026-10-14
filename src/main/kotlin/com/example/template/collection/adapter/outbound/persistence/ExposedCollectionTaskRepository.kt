package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import com.example.template.collection.domain.task.TaskRevision
import com.example.template.collection.domain.task.TaskTimeline
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
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

class ExposedCollectionTaskRepository(
    private val jsonMapper: JsonMapper,
) : CollectionTaskRepository {
    override fun insert(task: CollectionTask): CollectionTask {
        CollectionTasksTable.insert {
            it[id] = task.id
            it[createdAt] = task.createdAt.toStoredTime()
            writeTask(it, task)
        }
        return CollectionTasksTable
            .selectAll()
            .where { CollectionTasksTable.id eq task.id }
            .single()
            .toTask()
    }

    override fun findById(id: UUID): CollectionTask? =
        CollectionTasksTable
            .selectAll()
            .where { CollectionTasksTable.id eq id }
            .singleOrNull()
            ?.toTask()

    override fun findAll(
        offset: Int,
        limit: Int,
    ): List<CollectionTask> =
        CollectionTasksTable
            .selectAll()
            .orderBy(CollectionTasksTable.createdAt to SortOrder.ASC, CollectionTasksTable.id to SortOrder.ASC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toTask() }

    override fun update(
        task: CollectionTask,
        expectedVersion: Long,
    ): CollectionTask? {
        val updated =
            CollectionTasksTable.update({
                (CollectionTasksTable.id eq task.id) and (CollectionTasksTable.version eq expectedVersion)
            }) {
                writeTask(it, task)
            }
        if (updated == 0) return null
        return CollectionTasksTable
            .selectAll()
            .where { CollectionTasksTable.id eq task.id }
            .single()
            .toTask()
    }

    override fun findDue(
        now: Instant,
        limit: Int,
    ): List<CollectionTask> =
        CollectionTasksTable
            .selectAll()
            .where { (CollectionTasksTable.enabled eq true) and (CollectionTasksTable.nextRunAt lessEq now.toStoredTime()) }
            .orderBy(CollectionTasksTable.nextRunAt to SortOrder.ASC, CollectionTasksTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toTask() }

    override fun findUnscheduled(limit: Int): List<CollectionTask> =
        CollectionTasksTable
            .selectAll()
            .where { (CollectionTasksTable.enabled eq true) and CollectionTasksTable.nextRunAt.isNull() }
            .orderBy(CollectionTasksTable.createdAt to SortOrder.ASC, CollectionTasksTable.id to SortOrder.ASC)
            .limit(limit)
            .map { it.toTask() }

    override fun advanceNextRun(
        task: CollectionTask,
        expectedNextRunAt: Instant?,
    ): Boolean {
        val unchangedSlot =
            expectedNextRunAt?.let { CollectionTasksTable.nextRunAt eq it.toStoredTime() } ?: CollectionTasksTable.nextRunAt.isNull()
        val updated =
            CollectionTasksTable.update({
                (CollectionTasksTable.id eq task.id) and (CollectionTasksTable.version eq task.version) and unchangedSlot
            }) {
                it[nextRunAt] = task.nextRunAt?.toStoredTime()
            }
        return updated == 1
    }

    private fun writeTask(
        statement: UpdateBuilder<*>,
        task: CollectionTask,
    ) {
        statement[CollectionTasksTable.name] = task.definition.name
        statement[CollectionTasksTable.sourceId] = task.definition.sourceId
        statement[CollectionTasksTable.resource] = task.definition.target.resource
        statement[CollectionTasksTable.parameters] = jsonMapper.writeValueAsString(task.definition.target.parameters)
        statement[CollectionTasksTable.everySeconds] = task.definition.schedule.everySeconds
        statement[CollectionTasksTable.anchorAt] =
            task.definition.schedule.anchorAt
                .toStoredTime()
        statement[CollectionTasksTable.enabled] = task.enabled
        statement[CollectionTasksTable.version] = task.version
        statement[CollectionTasksTable.nextRunAt] = task.nextRunAt?.toStoredTime()
    }

    private fun ResultRow.toTask(): CollectionTask =
        CollectionTask.restore(
            id = this[CollectionTasksTable.id],
            definition = toDefinition(),
            revision = TaskRevision(this[CollectionTasksTable.enabled], this[CollectionTasksTable.version]),
            timeline =
                TaskTimeline(
                    createdAt = this[CollectionTasksTable.createdAt].toInstant(),
                    nextRunAt = this[CollectionTasksTable.nextRunAt]?.toInstant(),
                ),
        )

    private fun ResultRow.toDefinition(): TaskDefinition =
        TaskDefinition(
            name = this[CollectionTasksTable.name],
            sourceId = this[CollectionTasksTable.sourceId],
            target =
                CollectionTarget(
                    resource = this[CollectionTasksTable.resource],
                    parameters = jsonMapper.readValue(this[CollectionTasksTable.parameters], parameterType),
                ),
            schedule =
                IntervalSchedule(
                    everySeconds = this[CollectionTasksTable.everySeconds],
                    anchorAt = this[CollectionTasksTable.anchorAt].toInstant(),
                ),
        )

    // Every stored time is truncated to the column precision, so the conditional advance compares values the same way
    // they were read back.
    private fun Instant.toStoredTime(): OffsetDateTime = truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC)

    private companion object {
        val parameterType = object : TypeReference<Map<String, String>>() {}
    }
}
