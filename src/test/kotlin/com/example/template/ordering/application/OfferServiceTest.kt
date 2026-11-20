package com.example.template.ordering.application

import com.example.template.ordering.application.port.input.OfferNotFoundException
import com.example.template.ordering.application.port.output.OfferRepository
import com.example.template.ordering.application.service.OfferService
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.DuplicateOfferException
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import com.example.template.ordering.domain.offer.OfferStatus
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

// The repository is a strict mock: a port call a test does not stub fails the test. Rejecting a stored duplicate SKU
// belongs to the repository and is verified against H2 in ExposedOfferRepositoryTest.
class OfferServiceTest {
    private val sku = Sku("BOOK-001")
    private val repository = mockk<OfferRepository>()
    private val service = OfferService(repository)

    @Test
    fun `registration returns an offer on sale under a new id`() {
        every { repository.add(any()) } answers { firstArg() }

        val first = service.register(sku)
        val second = service.register(Sku("BOOK-002"))

        assertThat(first).isEqualTo(Offer.restore(first.id, sku, OfferStatus.ON_SALE))
        assertThat(first.id).isNotEqualTo(second.id)
    }

    @Test
    fun `a duplicate sku rejected by the repository reaches the caller`() {
        every { repository.add(any()) } throws DuplicateOfferException(sku, IllegalStateException("unique sku violated"))

        assertThatThrownBy { service.register(sku) }
            .isInstanceOf(DuplicateOfferException::class.java)
            .hasMessage("Offer already registered for SKU: BOOK-001")
    }

    @Test
    fun `get returns an offer and reports a missing one`() {
        val existing = Offer.register(OfferId(UUID.randomUUID()), sku)
        val missingId = OfferId(UUID.randomUUID())
        every { repository.findById(existing.id) } returns existing
        every { repository.findById(missingId) } returns null

        assertThat(service.get(existing.id)).isEqualTo(existing)
        assertThatThrownBy { service.get(missingId) }
            .isInstanceOf(OfferNotFoundException::class.java)
            .hasMessage("Offer not found: $missingId")
    }

    @Test
    fun `discontinuing an offer on sale returns it discontinued`() {
        val onSale = Offer.register(OfferId(UUID.randomUUID()), sku)
        val expected = Offer.restore(onSale.id, sku, OfferStatus.DISCONTINUED)
        every { repository.findByIdForUpdate(onSale.id) } returns onSale
        every { repository.update(any()) } answers { firstArg() }

        val discontinued = service.discontinue(onSale.id)

        assertThat(discontinued).isEqualTo(expected)
    }

    @Test
    fun `discontinuing again returns the discontinued offer unchanged`() {
        val discontinued = Offer.restore(OfferId(UUID.randomUUID()), sku, OfferStatus.DISCONTINUED)
        every { repository.findByIdForUpdate(discontinued.id) } returns discontinued

        assertThat(service.discontinue(discontinued.id)).isEqualTo(discontinued)
    }

    @Test
    fun `discontinuing a missing offer reports it as not found`() {
        val missingId = OfferId(UUID.randomUUID())
        every { repository.findByIdForUpdate(missingId) } returns null

        assertThatThrownBy { service.discontinue(missingId) }
            .isInstanceOf(OfferNotFoundException::class.java)
            .hasMessage("Offer not found: $missingId")
    }
}
