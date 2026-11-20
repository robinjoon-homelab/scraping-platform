package com.example.template.ordering.adapter.outbound.persistence

import com.example.template.ordering.application.port.output.OfferRepository
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.DuplicateOfferException
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

class ExposedOfferRepository : OfferRepository {
    // Ids are random UUIDs, so a unique violation means the SKU is taken. PostgreSQL makes the insert wait for a
    // concurrent uncommitted insert of the same SKU and then reports the violation, so that race ends here too.
    override fun add(offer: Offer): Offer {
        try {
            OffersTable.insert {
                it[id] = offer.id.value
                it[sku] = offer.sku.value
                it[status] = offer.status
            }
        } catch (exception: ExposedSQLException) {
            if (exception.sqlState == UNIQUE_VIOLATION) throw DuplicateOfferException(offer.sku, exception)
            throw exception
        }
        return selectById(offer.id).single().toOffer()
    }

    // The SKU never changes after registration, so only the status is written.
    override fun update(offer: Offer): Offer {
        val updated = OffersTable.update({ OffersTable.id eq offer.id.value }) { it[status] = offer.status }
        check(updated == 1) { "Offer to update does not exist: ${offer.id.value}" }
        return selectById(offer.id).single().toOffer()
    }

    override fun findById(id: OfferId): Offer? = selectById(id).singleOrNull()?.toOffer()

    override fun findByIdForUpdate(id: OfferId): Offer? = selectById(id).forUpdate().singleOrNull()?.toOffer()

    override fun findBySkus(skus: Set<Sku>): List<Offer> =
        OffersTable.selectAll().where { OffersTable.sku inList skus.map { it.value } }.map { it.toOffer() }

    private fun selectById(id: OfferId): Query = OffersTable.selectAll().where { OffersTable.id eq id.value }

    private fun ResultRow.toOffer(): Offer =
        Offer.restore(OfferId(this[OffersTable.id]), Sku(this[OffersTable.sku]), this[OffersTable.status])

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}
