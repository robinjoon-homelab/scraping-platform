package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.Dependency
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SliceAssignment
import com.tngtech.archunit.library.dependencies.SliceIdentifier
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

internal class ArchitectureRules(
    private val root: String,
    private val applicationClass: String = "TemplateApplication",
    private val additionalProductionTypes: Set<String> = emptySet(),
) {
    private val packages = ArchitecturePackages(root)

    val approvedLocations: ArchRule =
        classes().should(satisfy("belong to a production layer or an explicit bootstrap type", ::hasApprovedLocation))

    val pureDomain: ArchRule =
        classes()
            .that(inLayer(ArchitectureLayer.DOMAIN))
            .should()
            .onlyDependOnClassesThat(
                predicate("be language or domain types") { isLanguageType(it) || inLayer(it, ArchitectureLayer.DOMAIN) },
            )

    val pureApplication: ArchRule =
        classes()
            .that(predicate("are application types") { location(it)?.isApplication == true })
            .should(dependencies("target pure core types or service-owned declarative transactions", ::isAllowedApplicationDependency))

    val noCoreIo: ArchRule =
        noClasses()
            .that(predicate("are core types") { inLayer(it, ArchitectureLayer.DOMAIN) || location(it)?.isApplication == true })
            .should()
            .dependOnClassesThat(predicate("perform I/O", ::isIoType))

    val independentPorts: ArchRule =
        noClasses()
            .that(predicate("are ports") { location(it)?.isPort == true })
            .should()
            .dependOnClassesThat(
                inLayer(ArchitectureLayer.SERVICE, ArchitectureLayer.INBOUND, ArchitectureLayer.OUTBOUND, ArchitectureLayer.CONFIG),
            )

    val independentModels: ArchRule =
        classes()
            .that(inLayer(ArchitectureLayer.MODEL))
            .should()
            .onlyDependOnClassesThat(
                predicate("be language, domain, or application model types") {
                    isLanguageType(it) || inLayer(it, ArchitectureLayer.DOMAIN, ArchitectureLayer.MODEL)
                },
            )

    val inboundBoundary: ArchRule =
        noClasses()
            .that(inLayer(ArchitectureLayer.INBOUND))
            .should()
            .dependOnClassesThat(
                inLayer(ArchitectureLayer.OUTBOUND, ArchitectureLayer.OUTPUT_PORT, ArchitectureLayer.SERVICE, ArchitectureLayer.CONFIG),
            )

    val outboundBoundary: ArchRule =
        noClasses()
            .that(inLayer(ArchitectureLayer.OUTBOUND))
            .should()
            .dependOnClassesThat(
                inLayer(ArchitectureLayer.INBOUND, ArchitectureLayer.INPUT_PORT, ArchitectureLayer.SERVICE, ArchitectureLayer.CONFIG),
            )

    val independentAdapters: ArchRule =
        classes()
            .that(predicate("are adapters") { location(it)?.isAdapter == true })
            .should(dependencies("keep different adapter groups independent", ::isWithinSameAdapter))

    val featureEncapsulation: ArchRule =
        classes().should(dependencies("access another feature through its input contracts and pure values", ::respectsFeatureBoundary))

    val adapterTransactions: ArchRule =
        classes()
            .that(predicate("are adapters") { location(it)?.isAdapter == true })
            .should(satisfy("not declare transactions, including composed and inherited declarations", ::hasNoAdapterTransactions))

    val adapterTransactionInfrastructure: ArchRule =
        noClasses()
            .that(predicate("are adapters") { location(it)?.isAdapter == true })
            .should()
            .dependOnClassesThat(predicate("own Spring transactions") { type -> transactionInfrastructureTypes.any(type::isAssignableTo) })

    val repositoryTransactionOwnership: ArchRule =
        noClasses()
            .that(predicate("are persistence adapters") { location(it)?.isPersistence == true })
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.jetbrains.exposed..transactions..")

    val adapterManualTransactions: ArchRule =
        classes()
            .that(predicate("are adapters") { location(it)?.isAdapter == true })
            .should(
                satisfy("not control JDBC or Exposed transactions directly") { type ->
                    (type.methodCallsFromSelf + type.methodReferencesFromSelf).none { call ->
                        manualTransactionMethods.any { (owner, methods) ->
                            call.target.name in methods && call.target.owner.isAssignableTo(owner)
                        }
                    }
                },
            )

    val portContracts: ArchRule =
        classes()
            .that(predicate("have port role names") { location(it)?.isPort == true && hasPortRoleName(it) })
            .should()
            .beInterfaces()

    val constructorInjection: ArchRule =
        classes().should(satisfy("use constructor injection rather than field or method injection", ::usesConstructorInjection))

    val controllerRoles: ArchRule =
        classes()
            .that(predicate("are Spring controllers", ::isController))
            .should(
                satisfy("be named Controller in an inbound adapter") {
                    inLayer(it, ArchitectureLayer.INBOUND) &&
                        it.simpleName.endsWith("Controller")
                },
            )

    val controllerNames: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should(satisfy("belong to an inbound adapter") { inLayer(it, ArchitectureLayer.INBOUND) })

    val configurationRoles: ArchRule =
        classes()
            .that(predicate("are Spring configurations") { it.isAnnotatedWith(CONFIGURATION) || it.isMetaAnnotatedWith(CONFIGURATION) })
            .should(satisfy("be named Config in config or the application bootstrap", ::hasConfigurationRole))

    val repositoryRoles: ArchRule =
        classes()
            .that(predicate("are repositories or output-port implementations", ::isOutputAdapter))
            .should(
                satisfy("implement output ports in a matching outbound role") {
                    hasImplementationRole(it, ArchitectureLayer.OUTPUT_PORT, ArchitectureLayer.OUTBOUND)
                },
            )

    val inputPortImplementations: ArchRule =
        classes()
            .that(predicate("implement input ports") { implementedPorts(it, ArchitectureLayer.INPUT_PORT).isNotEmpty() })
            .should(
                satisfy("implement input ports in a matching application service") {
                    hasImplementationRole(it, ArchitectureLayer.INPUT_PORT, ArchitectureLayer.SERVICE)
                },
            )

    val inboundPortRoles: ArchRule =
        classes()
            .that(inLayer(ArchitectureLayer.INBOUND))
            .should(dependencies("call input ports with matching feature and role paths", ::hasInboundPortRole))

    val isolatedExposed: ArchRule =
        classes().should(
            dependencies(
                "isolate persistence technologies and allow JDBC in explicitly registered Flyway migrations",
                ::allowsPersistenceDependency,
            ),
        )

    val acyclicPackages: ArchRule =
        slices()
            .assignedFrom(
                object : SliceAssignment {
                    override fun getDescription(): String = "each production package"

                    override fun getIdentifierOf(javaClass: JavaClass): SliceIdentifier =
                        if (javaClass.packageName.startsWith("$root.")) {
                            SliceIdentifier.of(javaClass.packageName)
                        } else {
                            SliceIdentifier.ignore()
                        }
                },
            ).should()
            .beFreeOfCycles()

    fun all(): List<ArchRule> =
        listOf(
            approvedLocations,
            pureDomain,
            pureApplication,
            noCoreIo,
            independentPorts,
            independentModels,
            inboundBoundary,
            outboundBoundary,
            independentAdapters,
            featureEncapsulation,
            adapterTransactions,
            adapterTransactionInfrastructure,
            repositoryTransactionOwnership,
            adapterManualTransactions,
            portContracts,
            constructorInjection,
            controllerRoles,
            controllerNames,
            configurationRoles,
            repositoryRoles,
            inputPortImplementations,
            inboundPortRoles,
            isolatedExposed,
            acyclicPackages,
        ).map { it.allowEmptyShould(true) }

    private fun hasApprovedLocation(type: JavaClass): Boolean {
        if (type.name in bootstrapTypes || type.name in additionalProductionTypes) return true
        val position = location(type) ?: return false
        return !position.isAdapter || position.path.isNotEmpty()
    }

    private fun isAllowedApplicationDependency(dependency: Dependency): Boolean =
        isLanguageType(dependency.targetClass) ||
            inLayer(dependency.targetClass, ArchitectureLayer.DOMAIN) || location(dependency.targetClass)?.isApplication == true ||
            (inLayer(dependency.originClass, ArchitectureLayer.SERVICE) && dependency.targetClass.name in declarativeTransactionTypes)

    private fun isWithinSameAdapter(dependency: Dependency): Boolean {
        val target = location(dependency.targetClass) ?: return true
        return !target.isAdapter || location(dependency.originClass)?.sharesAdapterWith(target) == true
    }

    private fun respectsFeatureBoundary(dependency: Dependency): Boolean {
        val origin = location(dependency.originClass) ?: return true
        val target = location(dependency.targetClass) ?: return true
        if (origin.feature == target.feature || origin.layer == ArchitectureLayer.CONFIG) return true
        if (target.layer in publicFeatureLayers) return true
        return target.feature.isEmpty() && target.isPort
    }

    private fun hasInboundPortRole(dependency: Dependency): Boolean {
        val target = location(dependency.targetClass) ?: return true
        return target.layer != ArchitectureLayer.INPUT_PORT || !dependency.targetClass.isInterface ||
            location(dependency.originClass)?.matchesPort(target) == true
    }

    private fun allowsPersistenceDependency(dependency: Dependency): Boolean {
        val target = dependency.targetClass.packageName
        if (persistencePackages.none { target == it || target.startsWith("$it.") }) return true
        val origin = dependency.originClass
        if (location(origin)?.isPersistence == true || inLayer(origin, ArchitectureLayer.CONFIG)) return true
        val jdbc = listOf("java.sql", "javax.sql").any { target == it || target.startsWith("$it.") }
        return jdbc && origin.name in additionalProductionTypes && origin.isAssignableTo("org.flywaydb.core.api.migration.JavaMigration")
    }

    private fun hasImplementationRole(
        type: JavaClass,
        portLayer: ArchitectureLayer,
        implementationLayer: ArchitectureLayer,
    ): Boolean {
        val position = location(type) ?: return false
        return position.layer == implementationLayer && implementedPorts(type, portLayer).all(position::matchesPort)
    }

    private fun implementedPorts(
        type: JavaClass,
        layer: ArchitectureLayer,
    ): List<ArchitectureLocation> =
        if (type.isInterface) emptyList() else type.allRawInterfaces.mapNotNull(::location).filter { it.layer == layer }

    private fun isOutputAdapter(type: JavaClass): Boolean =
        !type.isInterface && (type.simpleName.endsWith("Repository") || implementedPorts(type, ArchitectureLayer.OUTPUT_PORT).isNotEmpty())

    private fun hasPortRoleName(type: JavaClass): Boolean = portRoleNames.any(type.simpleName::endsWith)

    private fun isController(type: JavaClass): Boolean =
        !type.isAnnotation && (type.isAnnotatedWith(CONTROLLER) || type.isMetaAnnotatedWith(CONTROLLER))

    private fun usesConstructorInjection(type: JavaClass): Boolean =
        (type.fields + type.methods).none { member ->
            injectionAnnotations.any { member.isAnnotatedWith(it) || member.isMetaAnnotatedWith(it) }
        }

    private fun hasNoAdapterTransactions(type: JavaClass): Boolean =
        (type.classHierarchy + type.allRawInterfaces).none(::hasTransactionAnnotation) && type.allMethods.none(::hasTransactionAnnotation)

    private fun hasTransactionAnnotation(element: CanBeAnnotated): Boolean =
        element.isAnnotatedWith(TRANSACTIONAL) || element.isMetaAnnotatedWith(TRANSACTIONAL)

    private fun hasConfigurationRole(type: JavaClass): Boolean =
        (inLayer(type, ArchitectureLayer.CONFIG) && type.simpleName.endsWith("Config")) ||
            (type.name == "$root.$applicationClass" && type.isAnnotatedWith(SPRING_BOOT_APPLICATION))

    private fun isLanguageType(type: JavaClass): Boolean =
        listOf("java", "kotlin", "org.jetbrains.annotations").any { type.packageName == it || type.packageName.startsWith("$it.") }

    private fun isIoType(type: JavaClass): Boolean =
        type.name in processIoTypes ||
            (type.name !in pureValueTypes && ioPackages.any { type.packageName == it || type.packageName.startsWith("$it.") })

    private fun location(type: JavaClass): ArchitectureLocation? = packages.location(type.packageName)

    private fun inLayer(
        type: JavaClass,
        vararg layers: ArchitectureLayer,
    ): Boolean = location(type)?.layer in layers

    private fun inLayer(vararg layers: ArchitectureLayer): DescribedPredicate<JavaClass> =
        predicate("belong to ${layers.joinToString()}") { inLayer(it, *layers) }

    private fun dependencies(
        description: String,
        test: (Dependency) -> Boolean,
    ): ArchCondition<JavaClass> = onlyHaveDependenciesWhere(DescribedPredicate.describe(description, test))

    private fun predicate(
        description: String,
        test: (JavaClass) -> Boolean,
    ): DescribedPredicate<JavaClass> = DescribedPredicate.describe(description, test)

    private fun satisfy(
        description: String,
        test: (JavaClass) -> Boolean,
    ): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(description) {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                events.add(SimpleConditionEvent(item, test(item), "${item.name} must $description"))
            }
        }

    private val bootstrapTypes: Set<String> get() = setOf("$root.$applicationClass", "$root.${applicationClass}Kt")

    private companion object {
        const val CONTROLLER = "org.springframework.stereotype.Controller"
        const val CONFIGURATION = "org.springframework.context.annotation.Configuration"
        const val SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication"
        const val TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional"
        val declarativeTransactionTypes =
            setOf(
                TRANSACTIONAL,
                "org.springframework.transaction.annotation.Propagation",
                "org.springframework.transaction.annotation.Isolation",
            )
        val transactionInfrastructureTypes =
            setOf(
                "org.springframework.transaction.TransactionManager",
                "org.springframework.transaction.support.TransactionOperations",
                "org.springframework.transaction.interceptor.TransactionAspectSupport",
            )
        val connectionTransactionMethods =
            setOf("commit", "rollback", "setAutoCommit", "setSavepoint", "releaseSavepoint", "setTransactionIsolation", "setReadOnly")
        val manualTransactionMethods =
            mapOf(
                "java.sql.Connection" to connectionTransactionMethods,
                "org.jetbrains.exposed.v1.jdbc.statements.api.ExposedConnection" to connectionTransactionMethods,
                "org.jetbrains.exposed.v1.core.Transaction" to setOf("commit", "rollback"),
            )
        val injectionAnnotations =
            listOf(
                "org.springframework.beans.factory.annotation.Autowired",
                "jakarta.inject.Inject",
                "javax.inject.Inject",
                "jakarta.annotation.Resource",
                "javax.annotation.Resource",
                "org.springframework.beans.factory.annotation.Value",
            )
        val publicFeatureLayers = setOf(ArchitectureLayer.DOMAIN, ArchitectureLayer.MODEL, ArchitectureLayer.INPUT_PORT)
        val portRoleNames =
            listOf("UseCase", "Repository", "Port", "Source", "Catalog", "Reporter", "Gateway", "Verifier", "Store", "Lookup", "Classifier")
        val pureValueTypes = setOf("java.io.Serializable", "java.net.URI")
        val persistencePackages = listOf("org.jetbrains.exposed", "org.springframework.jdbc", "java.sql", "javax.sql")
        val processIoTypes = setOf("java.lang.Process", "java.lang.ProcessBuilder", "java.lang.ProcessHandle", "java.lang.Runtime")
        val ioPackages =
            listOf(
                "java.io",
                "java.sql",
                "javax.sql",
                "java.net",
                "java.nio.file",
                "java.nio.channels",
                "java.rmi",
                "java.util.prefs",
                "kotlin.io",
            )
    }
}
