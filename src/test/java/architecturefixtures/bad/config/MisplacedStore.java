package architecturefixtures.bad.config;

import architecturefixtures.bad.application.port.output.BadOutput;
import architecturefixtures.bad.application.service.BadService;

class MisplacedStore implements BadOutput {
    public BadService service() {
        return new BadService();
    }
}
