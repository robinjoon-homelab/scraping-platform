package com.example.template.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ArchitecturePackagesTest {
    private val packages = ArchitecturePackages("example.app")

    @Test
    fun `layer-first and nested feature-first paths retain their boundaries`() {
        assertThat(packages.location("example.app.application.port.output.payment.refund"))
            .isEqualTo(ArchitectureLocation(emptyList(), ArchitectureLayer.OUTPUT_PORT, listOf("payment", "refund")))
        assertThat(packages.location("example.app.billing.orders.adapter.outbound.stripe.payment.refund"))
            .isEqualTo(ArchitectureLocation(listOf("billing", "orders"), ArchitectureLayer.OUTBOUND, listOf("stripe", "payment", "refund")))
        assertThat(packages.location("example.app.domain.collection"))
            .isEqualTo(ArchitectureLocation(emptyList(), ArchitectureLayer.DOMAIN, listOf("collection")))
    }

    @Test
    fun `unknown layers and similar root names cannot escape validation`() {
        listOf(
            "example.application.domain",
            "example.app2.domain",
            "example.app.misc",
            "example.app.billing.application.unknown.adapter.outbound.provider",
            "example.app.billing.adapter.middle",
        ).forEach { assertThat(packages.location(it)).describedAs(it).isNull() }
    }

    @Test
    fun `complete role paths match at any adapter depth`() {
        val port = requireNotNull(packages.location("example.app.application.port.output.payment.refund"))
        listOf("stripe.payment.refund", "payment.refund.stripe", "vendor.stripe.payment.refund.client").forEach { path ->
            val adapter = requireNotNull(packages.location("example.app.adapter.outbound.$path"))
            assertThat(adapter.matchesPort(port)).describedAs(path).isTrue()
        }
        listOf("payments.refund", "billing.refund", "payment.client.refund", "refund.payment", "refund", "stripe").forEach { path ->
            val adapter = requireNotNull(packages.location("example.app.adapter.outbound.$path"))
            assertThat(adapter.matchesPort(port)).describedAs(path).isFalse()
        }
    }

    @Test
    fun `feature identity uses the full prefix while root contracts remain shared`() {
        val port = requireNotNull(packages.location("example.app.billing.orders.application.port.output.source"))
        val same = requireNotNull(packages.location("example.app.billing.orders.adapter.outbound.vendor.source"))
        val other = requireNotNull(packages.location("example.app.shipping.orders.adapter.outbound.vendor.source"))
        assertThat(same.matchesPort(port)).isTrue()
        assertThat(other.matchesPort(port)).isFalse()
        val shared = requireNotNull(packages.location("example.app.application.port.output.source"))
        assertThat(same.matchesPort(shared)).isTrue()
    }

    @Test
    fun `adapter isolation is feature direction and the first declared group`() {
        val stripe = requireNotNull(packages.location("example.app.billing.adapter.outbound.stripe.payment.client"))
        val mapper = requireNotNull(packages.location("example.app.billing.adapter.outbound.stripe.mapping"))
        assertThat(stripe.sharesAdapterWith(mapper)).isTrue()
        listOf(
            "billing.adapter.outbound.paypal.payment",
            "shipping.adapter.outbound.stripe.payment",
            "billing.adapter.inbound.stripe.payment",
        ).forEach { path ->
            assertThat(stripe.sharesAdapterWith(requireNotNull(packages.location("example.app.$path")))).describedAs(path).isFalse()
        }
        val groupedStripe = requireNotNull(packages.location("example.app.adapter.outbound.payment.stripe"))
        val groupedPaypal = requireNotNull(packages.location("example.app.adapter.outbound.payment.paypal"))
        assertThat(groupedStripe.sharesAdapterWith(groupedPaypal)).isTrue()
    }
}
