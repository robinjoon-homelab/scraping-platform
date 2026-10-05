package architecturefixtures.good.application.port.input

interface CompanionPort {
    fun greeting(): String = GREETING

    companion object {
        const val GREETING = "hello"
    }
}

data class CreateCommand(
    val title: String,
)

data class CreateResult(
    val title: String,
)
