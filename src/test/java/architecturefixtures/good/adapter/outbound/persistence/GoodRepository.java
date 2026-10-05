package architecturefixtures.good.adapter.outbound.persistence;

import architecturefixtures.good.application.port.output.Output;
import architecturefixtures.good.domain.Value;
import org.jetbrains.exposed.v1.core.ResultRow;

public class GoodRepository implements Output {
    private ResultRow row;

    public Value get() {
        return new Value();
    }
}
