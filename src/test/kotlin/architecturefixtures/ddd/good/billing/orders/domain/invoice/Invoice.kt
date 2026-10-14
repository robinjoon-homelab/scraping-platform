package architecturefixtures.ddd.good.billing.orders.domain.invoice

import architecturefixtures.ddd.good.billing.orders.domain.order.OrderId
import architecturefixtures.ddd.good.domain.AggregateRoot
import architecturefixtures.ddd.good.domain.Money
import java.util.UUID

@JvmInline
value class InvoiceId(
    val value: UUID,
)

// A second aggregate of the same feature lives in its own package and refers to the order by identity.
// Calling the private constructor from the companion adds a public synthetic DefaultConstructorMarker bridge.
@AggregateRoot
class Invoice private constructor(
    val id: InvoiceId,
    val order: OrderId,
    val amount: Money,
) {
    companion object {
        fun issue(
            id: InvoiceId,
            order: OrderId,
            amount: Money,
        ): Invoice = Invoice(id, order, amount)
    }
}
