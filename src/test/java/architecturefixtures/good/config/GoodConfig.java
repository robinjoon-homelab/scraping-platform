package architecturefixtures.good.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import architecturefixtures.good.adapter.outbound.persistence.GoodRepository;
import architecturefixtures.good.application.port.input.Input;
import architecturefixtures.good.application.port.output.Output;
import architecturefixtures.good.application.service.GoodService;

@Configuration
class GoodConfig {
    @Bean
    Output output() {
        return new GoodRepository();
    }

    @Bean
    Input input(Output output) {
        return new GoodService(output);
    }
}
