package architecturefixtures.ddd.bad.billing.orders.domain.exposedcopy

import architecturefixtures.ddd.bad.domain.AggregateRoot
import java.util.UUID

@JvmInline
value class ExposedCopyId(
    val value: UUID,
)

// The constructor is private, but the explicit opt-out exposes the mangled copy-<hash> method.
@AggregateRoot
@ExposedCopyVisibility
data class ExposedCopyRoot private constructor(
    val id: ExposedCopyId,
) {
    companion object {
        fun create(id: ExposedCopyId): ExposedCopyRoot = ExposedCopyRoot(id)
    }
}
