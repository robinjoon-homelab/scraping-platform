package com.example.template.collection.adapter.inbound.web.view

import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class SourceForm(
    @field:NotBlank val name: String = "",
    @field:NotBlank val baseUrl: String = "",
) {
    fun definition() = SourceDefinition(name, SourceKind.HTTP_JSON, baseUrl)
}

data class PlanForm(
    @field:NotBlank val name: String = "",
    @field:NotBlank val sourceId: String = "",
    val resource: String = "/",
    @field:Size(max = 110000) val parameters: String = "",
    val everySeconds: Long = 3600,
    val anchorAt: String = Instant.now().toString(),
) {
    fun definition() =
        TaskDefinition(
            name,
            UUID.fromString(sourceId),
            CollectionTarget(resource, parameterMap()),
            IntervalSchedule(everySeconds, Instant.parse(anchorAt)),
        )

    private fun parameterMap(): Map<String, String> {
        val entries =
            parameters
                .lineSequence()
                .filter { it.isNotBlank() }
                .map {
                    require('=' in it) { "각 매개변수는 이름=값 형식이어야 합니다." }
                    it.substringBefore('=').trim() to it.substringAfter('=')
                }.toList()
        require(entries.map { it.first }.distinct().size == entries.size) { "매개변수 이름이 중복되었습니다." }
        return entries.toMap()
    }

    companion object {
        fun from(task: CollectionTask): PlanForm {
            val definition = task.definition
            return PlanForm(
                definition.name,
                definition.sourceId.toString(),
                definition.target.resource,
                definition.target.parameters.entries.joinToString("\n") {
                    "${it.key}=${it.value}"
                },
                definition.schedule.everySeconds,
                definition.schedule.anchorAt.toString(),
            )
        }
    }
}
