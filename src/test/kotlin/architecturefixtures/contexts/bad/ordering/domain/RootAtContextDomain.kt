package architecturefixtures.contexts.bad.ordering.domain

import architecturefixtures.contexts.bad.domain.AggregateRoot

// A root directly in the context's domain package would sit above every aggregate package of the context.
@AggregateRoot
class RootAtContextDomain private constructor(
    val id: String,
) {
    companion object {
        fun create(id: String): RootAtContextDomain = RootAtContextDomain(id)
    }
}
