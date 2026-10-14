package architecturefixtures.contexts.bad.ordering.domain.order

// A regular class on purpose. An inline value class would be erased from field types and leave no edge.
data class OrderId(
    val value: String,
)
