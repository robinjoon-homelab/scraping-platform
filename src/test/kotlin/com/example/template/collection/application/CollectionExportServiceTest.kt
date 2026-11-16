package com.example.template.collection.application

import com.example.template.collection.application.model.ExportStatus
import com.example.template.collection.application.port.output.CollectedRecordStore
import com.example.template.collection.application.port.output.CollectionExportStore
import com.example.template.collection.application.port.output.CollectionRunRepository
import com.example.template.collection.application.port.output.CollectionTaskRepository
import com.example.template.collection.application.service.CollectionExportService
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.CollectionTask
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CollectionExportServiceTest {
    private val tasks = mockk<CollectionTaskRepository>()
    private val exports = mockk<CollectionExportStore>()
    private val records = mockk<CollectedRecordStore>()
    private val runs = mockk<CollectionRunRepository>()
    private val service = CollectionExportService(tasks, exports, records, runs)
    private val now = Instant.parse("2026-11-16T00:00:00Z")
    private val task =
        CollectionTask.create(
            UUID.randomUUID(),
            TaskDefinition(
                "Items",
                UUID.randomUUID(),
                CollectionTarget("/items"),
                IntervalSchedule(60, now),
            ),
            now,
        )

    @Test
    fun `empty continuation preserves consumer position without claiming processing`() {
        every { tasks.findById(task.id) } returns task
        every { exports.status(task.id) } returns ExportStatus(task.id, true, 9, 3)
        every { exports.publish(task.id, 20) } returns Unit
        every { exports.page(task.id, 9, 20) } returns emptyList()
        val page = service.pull(task.id, 9, 20)
        assertThat(page.taskId).isEqualTo(task.id)
        assertThat(page.nextAfter).isEqualTo(9)
        assertThat(page.items).isEmpty()
    }

    @Test
    fun `disabled scopes and invalid bounds are rejected`() {
        every { tasks.findById(task.id) } returns task
        every { exports.status(task.id) } returns ExportStatus(task.id, false, 0, 0)
        assertThatThrownBy { service.pull(task.id, 0, 20) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
            .hasMessageContaining("disabled")
        assertThatThrownBy { service.pull(task.id, -1, 20) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { service.pull(task.id, 0, 101) }.isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `configuration returns persisted derived status`() {
        every { tasks.findById(task.id) } returns task
        every { exports.configure(task.id, true) } returns Unit
        every { exports.status(task.id) } returns ExportStatus(task.id, true, 9, 3)
        assertThat(service.configure(task.id, true)).isEqualTo(ExportStatus(task.id, true, 9, 3))
    }
}
