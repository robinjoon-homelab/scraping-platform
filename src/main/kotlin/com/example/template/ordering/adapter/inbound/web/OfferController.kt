package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OfferUseCase
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

// Offers are addressed by UUID, so the SKU format never has to be safe for a URL path.
@RestController
@RequestMapping("/api/offers")
class OfferController(
    private val offerUseCase: OfferUseCase,
) {
    @PostMapping
    fun register(
        @RequestBody request: RegisterOfferRequest,
    ): ResponseEntity<OfferResponse> {
        val offer = offerUseCase.register(Sku.of(request.sku))
        return ResponseEntity.created(URI.create("/api/offers/${offer.id.value}")).body(OfferResponse.from(offer))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): OfferResponse = OfferResponse.from(offerUseCase.get(OfferId(id)))

    @PatchMapping("/{id}/discontinue")
    fun discontinue(
        @PathVariable id: UUID,
    ): OfferResponse = OfferResponse.from(offerUseCase.discontinue(OfferId(id)))
}

// The SKU rules live in the domain. A missing or null sku fails JSON binding.
data class RegisterOfferRequest(
    val sku: String,
)

data class OfferResponse(
    val id: UUID,
    val sku: String,
    val status: String,
) {
    companion object {
        fun from(offer: Offer): OfferResponse = OfferResponse(id = offer.id.value, sku = offer.sku.value, status = offer.status.name)
    }
}
