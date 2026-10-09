package architecturefixtures.good.application.service;

import architecturefixtures.good.application.port.input.Input;
import architecturefixtures.good.application.port.output.Output;
import architecturefixtures.good.domain.Value;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class GoodService implements Input {
    private final Output output;

    public GoodService(Output output) {
        this.output = output;
    }

    public Value get() {
        return output.get();
    }

    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT)
    public Value refresh() {
        return output.get();
    }
}
