package architecturefixtures.bad.adapter.inbound.web;

import architecturefixtures.bad.adapter.outbound.persistence.BadPersistence;
import architecturefixtures.bad.application.port.output.BadOutput;
import architecturefixtures.bad.application.service.BadService;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BadController {
    BadPersistence persistence;
    BadOutput output;
    BadService service;
}
