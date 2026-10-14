package architecturefixtures.contexts.bad.adapter.outbound.persistence

import architecturefixtures.contexts.bad.ordering.application.model.OrderView

// A shared adapter that reads a context's model is no longer feature independent.
class BusinessAwareStore {
    fun keyOf(view: OrderView): String = view.orderId
}
