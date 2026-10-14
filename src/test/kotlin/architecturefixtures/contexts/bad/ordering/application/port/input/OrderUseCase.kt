package architecturefixtures.contexts.bad.ordering.application.port.input

interface OrderUseCase {
    fun place(quantity: Int): String
}
