package architecturefixtures.ddd.good.billing.orders.domain.order

import architecturefixtures.ddd.good.domain.AggregateRoot
import architecturefixtures.ddd.good.domain.Money
import java.util.UUID

@JvmInline
value class OrderId(
    val value: UUID,
)

// Feature-first aggregate. The value class id mangles copy to copy-<hash> and the default argument adds a
// synthetic DefaultConstructorMarker constructor, which the private constructor and copy rules must accept.
@AggregateRoot
@ConsistentCopyVisibility
data class Order private constructor(
    val id: OrderId,
    val total: Money,
    val lines: List<OrderLine> = emptyList(),
) {
    fun add(line: OrderLine): Order = copy(lines = lines + line, total = Money(total.cents() + line.price.cents()))

    companion object {
        fun place(id: OrderId): Order = Order(id, Money(0))
    }
}

data class OrderLine(
    val sku: String,
    val price: Money,
)

// A helper in the aggregate package belongs to the aggregate and may use its root.
object OrderPolicy {
    fun isEmpty(order: Order): Boolean = order.lines.isEmpty()
}
