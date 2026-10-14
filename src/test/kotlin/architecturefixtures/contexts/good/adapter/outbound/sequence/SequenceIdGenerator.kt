package architecturefixtures.contexts.good.adapter.outbound.sequence

import architecturefixtures.contexts.good.application.port.output.IdGenerator

class SequenceIdGenerator : IdGenerator {
    private var last = 0

    override fun next(): String = (++last).toString()
}
