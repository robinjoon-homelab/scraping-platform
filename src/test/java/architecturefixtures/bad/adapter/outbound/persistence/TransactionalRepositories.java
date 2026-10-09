package architecturefixtures.bad.adapter.outbound.persistence;

import architecturefixtures.bad.config.ComposedTransaction;
import architecturefixtures.bad.config.TransactionSources;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClassTransactionalRepository {}

class MethodTransactionalRepository {
    @Transactional
    public void execute() {}
}

@ComposedTransaction
class ComposedClassTransactionalRepository {}

class ComposedMethodTransactionalRepository {
    @ComposedTransaction
    public void execute() {}
}

class InheritedClassTransactionalRepository extends TransactionSources.ClassTransactionalBase {}

class InheritedMethodTransactionalRepository extends TransactionSources.MethodTransactionalBase {
    @Override
    public void execute() {}
}

class InterfaceClassTransactionalRepository implements TransactionSources.ClassTransactionalContract {
    @Override
    public void execute() {}
}

class InterfaceMethodTransactionalRepository implements TransactionSources.MethodTransactionalContract {
    @Override
    public void execute() {}
}
