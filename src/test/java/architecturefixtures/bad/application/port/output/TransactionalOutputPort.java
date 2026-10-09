package architecturefixtures.bad.application.port.output;

import org.springframework.transaction.annotation.Transactional;

interface TransactionalOutputPort {
    @Transactional(readOnly = true)
    String get();
}
