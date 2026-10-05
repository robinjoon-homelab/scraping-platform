package architecturefixtures.good.adapter.inbound.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import architecturefixtures.good.application.port.input.Input;

@RestController
class GoodController {
    private final Input input;

    @Autowired
    GoodController(Input input) {
        this.input = input;
    }
}
