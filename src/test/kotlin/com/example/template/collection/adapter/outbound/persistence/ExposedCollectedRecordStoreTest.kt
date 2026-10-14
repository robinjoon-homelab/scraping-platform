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

    private fun rowsOf(run: SeededRun) =
        inTransaction { CollectedRecordsTable.selectAll().where { CollectedRecordsTable.runId eq run.runId }.toList() }

    private fun sha256(text: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private companion object {
        const val REVISION_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val REVISION_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
