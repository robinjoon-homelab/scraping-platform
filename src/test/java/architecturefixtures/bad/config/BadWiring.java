package architecturefixtures.bad.config;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

class FieldInjected {
    @Autowired Object collaborator;
}

class MethodInjected {
    @Autowired void setCollaborator(Object collaborator) {}
}

class ResourceFieldInjected {
    @Resource Object collaborator;
}

class ResourceMethodInjected {
    @Resource void setCollaborator(Object collaborator) {}
}

class ValueFieldInjected {
    @Value("${sample.value}") String value;
}
