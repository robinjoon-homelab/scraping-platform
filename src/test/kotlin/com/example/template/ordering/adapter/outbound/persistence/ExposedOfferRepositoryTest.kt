package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.DuplicateOfferException
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import com.example.template.ordering.domain.offer.OfferStatus
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.util.UUID

class ExposedOfferRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedOfferRepository()

    private val sku = Sku("KB-001")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `every status round trips and a missing id reads as null`() {
        OfferStatus.entries.forEach { status ->
            val offer = Offer.restore(newId(), Sku("KB-$status"), status)

            assertThat(inTransaction { repository.add(offer) }).isEqualTo(offer)
            assertThat(inTransaction { repository.findById(offer.id) }).isEqualTo(offer)
        }
        assertThat(inTransaction { repository.findById(newId()) }).isNull()
    }

    @Test
    fun `adding a registered sku under another id fails as a duplicate and keeps the cause`() {
        inTransaction { repository.add(Offer.register(newId(), sku)) }

        assertThatThrownBy { inTransaction { repository.add(Offer.register(newId(), sku)) } }
            .isInstanceOf(DuplicateOfferException::class.java)
            .hasMessage("Offer already registered for SKU: KB-001")
            .hasCauseInstanceOf(SQLException::class.java)
    }

    @Test
    fun `update writes the status and keeps the registered sku`() {
        val stored = inTransaction { repository.add(Offer.register(newId(), sku)) }
        val withOtherSku = Offer.restore(stored.id, Sku("KB-999"), OfferStatus.DISCONTINUED)
        val expected = Offer.restore(stored.id, sku, OfferStatus.DISCONTINUED)

        assertThat(inTransaction { repository.update(withOtherSku) }).isEqualTo(expected)
        assertThat(inTransaction { repository.findById(stored.id) }).isEqualTo(expected)
    }

    @Test
    fun `updating a missing offer fails instead of inserting it`() {
        val missing = Offer.register(newId(), sku)

        assertThatIllegalStateException().isThrownBy { inTransaction { repository.update(missing) } }
        assertThat(inTransaction { repository.findById(missing.id) }).isNull()
    }

    @Test
    fun `findByIdForUpdate returns the stored offer or null`() {
        val stored = inTransaction { repository.add(Offer.register(newId(), sku)) }

        assertThat(inTransaction { repository.findByIdForUpdate(stored.id) }).isEqualTo(stored)
        assertThat(inTransaction { repository.findByIdForUpdate(newId()) }).isNull()
    }

    @Test
    fun `findBySkus returns the offers of the requested skus in any status`() {
        val onSale = inTransaction { repository.add(Offer.register(newId(), Sku("KB-001"))) }
        val discontinued = inTransaction { repository.add(Offer.restore(newId(), Sku("KB-002"), OfferStatus.DISCONTINUED)) }
        inTransaction { repository.add(Offer.register(newId(), Sku("KB-003"))) }

        assertThat(inTransaction { repository.findBySkus(setOf(Sku("KB-001"), Sku("KB-002"), Sku("KB-404"))) })
            .containsExactlyInAnyOrder(onSale, discontinued)
        assertThat(inTransaction { repository.findBySkus(emptySet()) }).isEmpty()
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)

    private fun newId() = OfferId(UUID.randomUUID())
}
