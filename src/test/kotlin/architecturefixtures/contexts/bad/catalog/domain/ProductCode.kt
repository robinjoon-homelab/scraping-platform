package architecturefixtures.contexts.bad.catalog.domain

// A regular class on purpose. A value class would be erased from field and parameter types and leave no edge.
data class ProductCode(
    val value: String,
)
