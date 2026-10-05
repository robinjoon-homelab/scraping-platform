package architecturefixtures.good.application.service;

import architecturefixtures.good.application.port.input.Input;
import architecturefixtures.good.application.port.output.Output;
import architecturefixtures.good.domain.Value;

public class GoodService implements Input {
    private final Output output;

    public GoodService(Output output) {
        this.output = output;
    }

    public Value get() {
        return output.get();
    }
}
