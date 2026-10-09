package com.example.template.adapter.inbound.web

import com.example.template.domain.CollectionTarget
import com.example.template.domain.IntervalSchedule
import com.example.template.domain.InvalidCollectionConfigurationException
import com.example.template.domain.TaskDefinition
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class TaskDefinitionRequest(
    val name: String,
    val sourceId: UUID,
    val target: CollectionTargetRequest,
    @field:Valid
    val schedule: IntervalScheduleRequest,
) {
    fun toDefinition(): TaskDefinition = TaskDefinition(name, sourceId, target.toTarget(), schedule.toSchedule())
}

data class CollectionTargetRequest(
    val resource: String,
    val parameters: Map<String, String?> = emptyMap(),
) {
    fun toTarget(): CollectionTarget {
        val values =
            parameters.mapValues { (_, value) ->
                value ?: throw InvalidCollectionConfigurationException("parameters must not contain null values")
            }
        return CollectionTarget(resource, values)
    }
}

data class IntervalScheduleRequest(
    @field:NotNull
    val everySeconds: Long?,
    val anchorAt: Instant,
) {
    fun toSchedule(): IntervalSchedule = IntervalSchedule(requireNotNull(everySeconds), anchorAt)
}

data class ReplaceTaskRequest(
    @field:NotNull
    val expectedVersion: Long?,
    @field:Valid
    val definition: TaskDefinitionRequest,
)

data class SetTaskEnabledRequest(
    @field:NotNull
    val expectedVersion: Long?,
    @field:NotNull
    val enabled: Boolean?,
)
