package architecturefixtures.bad.config;

import org.springframework.transaction.annotation.Transactional;

public final class TransactionSources {
    @Transactional
    public static class ClassTransactionalBase {}

    public static class MethodTransactionalBase {
        @Transactional
        public void execute() {}
    }

    @Transactional
    public interface ClassTransactionalContract {
        void execute();
    }

    public interface MethodTransactionalContract {
        @Transactional
        void execute();
    }
}
