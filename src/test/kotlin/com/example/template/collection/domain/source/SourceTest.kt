package com.example.template.collection.domain.source

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class SourceTest {
    private val definition = SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com")

    @Test
    fun `생성은 이름을 정리하고 전달받은 정의를 변경하지 않는다`() {
        val id = UUID.randomUUID()
        val createdAt = Instant.EPOCH
        val original = definition.copy(name = "  공개 데이터  ")

        val created = Source.create(id, original, createdAt)

        assertThat(created.id).isEqualTo(id)
        assertThat(created.definition.name).isEqualTo("공개 데이터")
        assertThat(created.createdAt).isEqualTo(createdAt)
        assertThat(original.name).isEqualTo("  공개 데이터  ")
    }

    @ParameterizedTest
    @ValueSource(strings = ["1970-01-01T00:00:00Z", "9999-12-31T23:59:59.999999999Z"])
    fun `생성 시각은 저장 가능한 연도 범위의 양 끝을 허용한다`(timestamp: String) {
        val createdAt = Instant.parse(timestamp)
        val source = Source.create(UUID.randomUUID(), definition, createdAt)

        assertThat(source.createdAt).isEqualTo(createdAt)
        assertThat(source.definition).isEqualTo(definition)
    }

    @ParameterizedTest
    @ValueSource(strings = ["1969-12-31T23:59:59.999999999Z", "+10000-01-01T00:00:00Z"])
    fun `생성 시각이 저장 가능 범위를 1나노초라도 벗어나면 거부한다`(timestamp: String) {
        assertThatThrownBy { Source.create(UUID.randomUUID(), definition, Instant.parse(timestamp)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }
}
