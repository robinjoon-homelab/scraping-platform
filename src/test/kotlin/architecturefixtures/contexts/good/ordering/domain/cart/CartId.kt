package architecturefixtures.contexts.good.ordering.domain.cart

// A regular class on purpose. An inline value class would be erased from field types and leave no edge to check.
data class CartId(
    val value: String,
)
