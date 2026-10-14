package com.example.template.collection.domain.run

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

class CollectedRecordTest {
    private val collectedAt = Instant.parse("2026-10-05T00:00:00Z")
    private val identity = RecordIdentity("prices/cpi-2026-09", "a".repeat(64))
    private val provenance = RecordProvenance(UUID.randomUUID(), UUID.randomUUID(), collectedAt)

    @ParameterizedTest
    @ValueSource(ints = [1, 1_000_000])
    fun `payload 문자 수의 양쪽 경계는 허용한다`(length: Int) {
        val record = CollectedRecord(identity, "v1", "가".repeat(length), provenance)

        assertThat(record.payload).hasSize(length)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1_000_001])
    fun `빈 payload와 크기를 초과한 payload는 거부한다`(length: Int) {
        assertThatThrownBy { CollectedRecord(identity, "v1", "a".repeat(length), provenance) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["A", "g"])
    fun `revision hash에는 소문자 16진수만 허용한다`(character: String) {
        assertThatThrownBy { RecordIdentity("prices-1", character.repeat(64)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [63, 65])
    fun `revision hash는 정확히 64자여야 한다`(length: Int) {
        assertThatThrownBy { RecordIdentity("prices-1", "a".repeat(length)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `빈 원본 키나 과도하게 긴 원본 키는 거부한다`() {
        assertThat(RecordIdentity("a".repeat(2048), identity.revisionHash).sourceRecordKey).hasSize(2048)
        assertThatThrownBy { RecordIdentity(" ", identity.revisionHash) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
        assertThatThrownBy { RecordIdentity("a".repeat(2049), identity.revisionHash) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }
}
