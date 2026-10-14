package architecturefixtures.contexts.bad.inventory.domain

import architecturefixtures.contexts.bad.catalog.domain.ProductCode

// Holds a value of an undeclared feature's domain: the context core may not share another feature's model.
class ForeignDomainInCore(
    val product: ProductCode,
)
