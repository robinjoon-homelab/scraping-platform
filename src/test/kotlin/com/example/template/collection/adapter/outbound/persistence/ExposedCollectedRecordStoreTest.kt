package com.example.template.collection.adapter.outbound.persistence

import com.example.template.collection.domain.run.CollectedRecord
import com.example.template.collection.domain.run.RecordIdentity
import com.example.template.collection.domain.run.RecordProvenance
import com.example.template.support.persistence.MigratedH2Database
import com.example.template.support.persistence.SeededRun
import com.example.template.support.persistence.seedRun
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

class ExposedCollectedRecordStoreTest {
    private val database = MigratedH2Database()
    private val store = ExposedCollectedRecordStore()
    private val collectedAt = Instant.parse("2026-10-05T00:00:00.123456789Z")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `new records are stored with the key hash and the time precision of the database`() {
        val run = database.seedRun()
        val updatedAt = Instant.parse("2020-02-29T12:30:00Z")

        val saved =
            inTransaction {
                store.saveNew(
                    listOf(record(run, "/prices?page=1", sourceUpdatedAt = updatedAt), record(run, "/prices?page=2")),
                )
            }

        assertThat(saved).isEqualTo(2)
        val rows = rowsOf(run)
        assertThat(rows).hasSize(2)
        val first = rows.single { it[CollectedRecordsTable.sourceRecordKey] == "/prices?page=1" }
        assertThat(first[CollectedRecordsTable.sourceRecordKeyHash]).isEqualTo(sha256("/prices?page=1"))
        assertThat(first[CollectedRecordsTable.revisionHash]).isEqualTo(REVISION_A)
        assertThat(first[CollectedRecordsTable.runId]).isEqualTo(run.runId)
        assertThat(first[CollectedRecordsTable.schemaVersion]).isEqualTo("http-json/1")
        assertThat(first[CollectedRecordsTable.payload]).isEqualTo("{\"price\":1}")
        assertThat(first[CollectedRecordsTable.collectedAt].toInstant()).isEqualTo(Instant.parse("2026-10-05T00:00:00.123456Z"))
        assertThat(first[CollectedRecordsTable.sourceUpdatedAt]?.toInstant()).isEqualTo(updatedAt)
        assertThat(
            rows.single { it[CollectedRecordsTable.sourceRecordKey] == "/prices?page=2" }[CollectedRecordsTable.sourceUpdatedAt],
        ).isNull()
    }

    @Test
    fun `a content version stored before is ignored and keeps the run that observed it first`() {
        val run = database.seedRun()
        val laterRun = database.seedRun(sameTaskAs = run)
        inTransaction { store.saveNew(listOf(record(run, "/prices"))) }

        val saved = inTransaction { store.saveNew(listOf(record(laterRun, "/prices"), record(laterRun, "/other"))) }

        assertThat(saved).isEqualTo(1)
        assertThat(rowsOf(run).single()[CollectedRecordsTable.runId]).isEqualTo(run.runId)
        assertThat(rowsOf(laterRun).single()[CollectedRecordsTable.sourceRecordKey]).isEqualTo("/other")
    }

    @Test
    fun `a repeated version inside one batch is stored once and a changed revision or another source is new`() {
        val run = database.seedRun()
        val otherSource = database.seedRun()

        val saved =
            inTransaction {
                store.saveNew(
                    listOf(
                        record(run, "/prices"),
                        record(run, "/prices"),
                        record(run, "/prices", revisionHash = REVISION_B),
                        record(otherSource, "/prices"),
                    ),
                )
            }

        assertThat(saved).isEqualTo(3)
        assertThat(rowsOf(run).map { it[CollectedRecordsTable.revisionHash] }).containsExactlyInAnyOrder(REVISION_A, REVISION_B)
        assertThat(rowsOf(otherSource)).hasSize(1)
        assertThat(inTransaction { store.saveNew(emptyList()) }).isZero()
    }

    @Test
    fun `a run lists the records it stored first by collection time without their payload`() {
        val run = database.seedRun()
        val laterRun = database.seedRun(sameTaskAs = run)
        val updatedAt = Instant.parse("2020-02-29T12:30:00Z")
        inTransaction {
            store.saveNew(
                listOf(
                    record(run, "/late").at(collectedAt.plusSeconds(20)).withPayload("{\"name\":\"가나다\"}"),
                    record(run, "/early").updatedAt(updatedAt),
                    record(run, "/middle").at(collectedAt.plusSeconds(10)),
                ),
            )
            store.saveNew(listOf(record(laterRun, "/early"), record(laterRun, "/other")))
        }

        val summaries = inTransaction { store.findFirstObservedBy(run.runId, 0, 10) }

        assertThat(summaries.map { it.identity.sourceRecordKey }).containsExactly("/early", "/middle", "/late")
        val early = summaries.first()
        assertThat(early.identity.revisionHash).isEqualTo(REVISION_A)
        assertThat(early.schemaVersion).isEqualTo("http-json/1")
        assertThat(early.collectedAt).isEqualTo(Instant.parse("2026-10-05T00:00:00.123456Z"))
        assertThat(early.sourceUpdatedAt).isEqualTo(updatedAt)
        assertThat(early.payloadBytes).isEqualTo(11)
        assertThat(summaries[1].sourceUpdatedAt).isNull()
        assertThat(summaries.last().payloadBytes).isEqualTo(20)
        assertThat(
            inTransaction { store.findFirstObservedBy(laterRun.runId, 0, 10) }.map { it.identity.sourceRecordKey },
        ).containsExactly("/other")
    }

    @Test
    fun `payload size is the utf-8 byte count for supplementary plane characters too`() {
        val run = database.seedRun()
        inTransaction {
            store.saveNew(
                listOf(
                    record(run, "/emoji").withPayload("{\"name\":\"😀\"}"),
                    record(run, "/hangul").withPayload("{\"name\":\"한글\"}").at(collectedAt.plusSeconds(1)),
                ),
            )
        }

        val sizes =
            inTransaction {
                store.findFirstObservedBy(
                    run.runId,
                    0,
                    10,
                )
            }.associate { it.identity.sourceRecordKey to it.payloadBytes }

        assertThat(sizes).containsEntry("/emoji", 15).containsEntry("/hangul", 17)
    }

    @Test
    fun `record pages follow the offset and limit and ties in collection time are ordered by id`() {
        val run = database.seedRun()
        inTransaction { store.saveNew(listOf("/a", "/b", "/c", "/d").map { record(run, it) }) }
        val all = inTransaction { store.findFirstObservedBy(run.runId, 0, 10) }

        val page = inTransaction { store.findFirstObservedBy(run.runId, 1, 2) }

        assertThat(all.map { it.id.toString() }).isSorted()
        assertThat(page).containsExactlyElementsOf(all.subList(1, 3))
        assertThat(inTransaction { store.findFirstObservedBy(run.runId, 4, 2) }).isEmpty()
        assertThat(inTransaction { store.findFirstObservedBy(UUID.randomUUID(), 0, 2) }).isEmpty()
    }

    @Test
    fun `a record is found by id with its payload and provenance`() {
        val run = database.seedRun()
        val updatedAt = Instant.parse("2020-02-29T12:30:00Z")
        inTransaction { store.saveNew(listOf(record(run, "/prices").updatedAt(updatedAt).withPayload("{\"price\":\"가격\"}"))) }
        val id = inTransaction { store.findFirstObservedBy(run.runId, 0, 1) }.single().id

        val found = requireNotNull(inTransaction { store.findById(id) })

        assertThat(found.id).isEqualTo(id)
        assertThat(found.record.identity).isEqualTo(RecordIdentity("/prices", REVISION_A))
        assertThat(found.record.schemaVersion).isEqualTo("http-json/1")
        assertThat(found.record.payload).isEqualTo("{\"price\":\"가격\"}")
        assertThat(found.record.provenance)
            .isEqualTo(RecordProvenance(run.runId, run.sourceId, Instant.parse("2026-10-05T00:00:00.123456Z"), updatedAt))
        assertThat(inTransaction { store.findById(UUID.randomUUID()) }).isNull()
    }

    private fun record(
        run: SeededRun,
        key: String,
        revisionHash: String = REVISION_A,
        sourceUpdatedAt: Instant? = null,
    ) = CollectedRecord(
        identity = RecordIdentity(key, revisionHash),
        schemaVersion = "http-json/1",
        payload = "{\"price\":1}",
        provenance = RecordProvenance(run.runId, run.sourceId, collectedAt, sourceUpdatedAt),
    )

    private fun CollectedRecord.at(time: Instant) = copy(provenance = provenance.copy(collectedAt = time))

    private fun CollectedRecord.updatedAt(time: Instant) = copy(provenance = provenance.copy(sourceUpdatedAt = time))

    private fun CollectedRecord.withPayload(text: String) = copy(payload = text)

    private fun rowsOf(run: SeededRun) =
        inTransaction { CollectedRecordsTable.selectAll().where { CollectedRecordsTable.runId eq run.runId }.toList() }

    private fun sha256(text: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private companion object {
        const val REVISION_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val REVISION_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
