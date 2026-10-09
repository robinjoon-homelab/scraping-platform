package architecturefixtures.bad.adapter.inbound.web;

import architecturefixtures.bad.config.ComposedTransaction;
import architecturefixtures.bad.config.TransactionSources;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ClassTransactionalController {}

class MethodTransactionalController {
    @Transactional
    public void execute() {}
}

@ComposedTransaction
class ComposedClassTransactionalController {}

class ComposedMethodTransactionalController {
    @ComposedTransaction
    public void execute() {}
}

class InheritedClassTransactionalController extends TransactionSources.ClassTransactionalBase {}

class InheritedMethodTransactionalController extends TransactionSources.MethodTransactionalBase {
    @Override
    public void execute() {}
}

class InterfaceClassTransactionalController implements TransactionSources.ClassTransactionalContract {
    @Override
    public void execute() {}
}

class InterfaceMethodTransactionalController implements TransactionSources.MethodTransactionalContract {
    @Override
    public void execute() {}
}
