package architecturefixtures.bad.application.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionTemplateService {
    TransactionTemplate transactions;
}

class TransactionManagerService {
    PlatformTransactionManager manager;
}
