package architecturefixtures.contexts.good.application.port.output

// A shared root port. It names no feature, so every context core may use it.
interface IdGenerator {
    fun next(): String
}
