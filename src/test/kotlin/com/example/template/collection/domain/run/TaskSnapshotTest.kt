package com.example.template.collection.domain.run

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import com.example.template.collection.domain.source.SourceDefinition
import com.example.template.collection.domain.source.SourceKind
import com.example.template.collection.domain.task.CollectionTarget
import com.example.template.collection.domain.task.IntervalSchedule
import com.example.template.collection.domain.task.TaskDefinition
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TaskSnapshotTest {
    private val source =
        SourceSnapshot(UUID.randomUUID(), SourceDefinition("Source", SourceKind.HTTP_JSON, "https://example.com"), Instant.EPOCH)

    @Test
    fun `snapshot retains independent immutable configuration values`() {
        val parameters = mutableMapOf("series" to "CPI")
        val definition = definition(CollectionTarget("/observations", parameters))
        val snapshot = TaskSnapshot(UUID.randomUUID(), 3, definition, source)

        parameters.clear()
        val replacement = definition.copy(name = "Revised")

        assertThat(snapshot.definition.name).isEqualTo("Original")
        assertThat(snapshot.definition.target.parameters).containsEntry("series", "CPI")
        assertThat(replacement.name).isEqualTo("Revised")
        assertThat(snapshot.source).isEqualTo(source)
        assertThat(snapshot.version).isEqualTo(3)
    }

    @Test
    fun `snapshot rejects mismatching sources and negative task versions`() {
        val definition = definition(CollectionTarget("/observations"))

        assertThatThrownBy { TaskSnapshot(UUID.randomUUID(), 0, definition.copy(sourceId = UUID.randomUUID()), source) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { TaskSnapshot(UUID.randomUUID(), -1, definition, source) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    private fun definition(target: CollectionTarget): TaskDefinition =
        TaskDefinition("Original", source.id, target, IntervalSchedule(60, Instant.EPOCH))
}
