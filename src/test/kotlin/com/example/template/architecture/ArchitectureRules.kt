package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SliceAssignment
import com.tngtech.archunit.library.dependencies.SliceIdentifier
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

internal class ArchitectureRules(
    private val root: String,
) {
    private val layers =
        listOf(
            "domain",
            "application.port.input",
            "application.port.output",
            "application.service",
            "adapter.inbound.web",
            "adapter.outbound.persistence",
            "config",
        )

    val approvedLocations: ArchRule =
        classes().should(satisfy("belong to an approved production package or bootstrap class", ::hasApprovedLocation))

    val pureDomain: ArchRule =
        classes()
            .that()
            .resideInAPackage("$root.domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "kotlin..", "org.jetbrains.annotations..", "$root.domain..")

    val pureApplication: ArchRule =
        classes()
            .that()
            .resideInAPackage("$root.application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "kotlin..", "org.jetbrains.annotations..", "$root.domain..", "$root.application..")

    val noCoreIo: ArchRule =
        noClasses()
            .that()
            .resideInAnyPackage("$root.domain..", "$root.application..")
            .should()
            .dependOnClassesThat(predicate("perform I/O", ::isIoType))

    val independentPorts: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("$root.application.port..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("$root.application.service..", "$root.adapter..", "$root.config..")

    val inboundBoundary: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("$root.adapter.inbound..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "$root.adapter.outbound..",
                "$root.application.port.output..",
                "$root.application.service..",
                "$root.config..",
            )

    val outboundBoundary: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("$root.adapter.outbound..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "$root.adapter.inbound..",
                "$root.application.port.input..",
                "$root.application.service..",
                "$root.config..",
            )

    val portContracts: ArchRule =
        classes()
            .that()
            .resideInAPackage("$root.application.port..")
            .and(predicate("have a use-case, repository, or port role name", ::hasPortRoleName))
            .should()
            .beInterfaces()

    val constructorInjection: ArchRule =
        classes().should(satisfy("use constructor injection rather than field or method injection", ::usesConstructorInjection))

    val controllerRoles: ArchRule =
        classes()
            .that(predicate("are Spring controllers", ::isController))
            .should()
            .resideInAPackage("$root.adapter.inbound.web..")
            .andShould()
            .haveSimpleNameEndingWith("Controller")

    val controllerNames: ArchRule =
        classes()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .resideInAPackage("$root.adapter.inbound.web..")

    val configurationRoles: ArchRule =
        classes()
            .that(predicate("are Spring configurations", ::isConfiguration))
            .should(satisfy("be named Config in config or the application bootstrap", ::hasConfigurationRole))

    val repositoryRoles: ArchRule =
        classes()
            .that(predicate("are repository or output-port implementations", ::isOutputAdapter))
            .should()
            .resideInAPackage("$root.adapter.outbound.persistence..")

    val isolatedExposed: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackages("$root.adapter.outbound.persistence..", "$root.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.jetbrains.exposed..")

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
            inboundBoundary,
            outboundBoundary,
            portContracts,
            constructorInjection,
            controllerRoles,
            controllerNames,
            configurationRoles,
            repositoryRoles,
            isolatedExposed,
            acyclicPackages,
        ).map { it.allowEmptyShould(true) }

    private fun hasApprovedLocation(type: JavaClass): Boolean =
        type.name in setOf("$root.TemplateApplication", "$root.TemplateApplicationKt") ||
            layers.any { within(type, it) }

    private fun hasPortRoleName(type: JavaClass): Boolean = listOf("UseCase", "Repository", "Port").any(type.simpleName::endsWith)

    private fun isController(type: JavaClass): Boolean =
        !type.isAnnotation && (type.isAnnotatedWith(CONTROLLER) || type.isMetaAnnotatedWith(CONTROLLER))

    private fun isOutputAdapter(type: JavaClass): Boolean =
        !type.isInterface &&
            (type.simpleName.endsWith("Repository") || type.allRawInterfaces.any { within(it, "application.port.output") })

    private fun usesConstructorInjection(type: JavaClass): Boolean =
        (type.fields + type.methods).none { member ->
            injectionAnnotations.any { member.isAnnotatedWith(it) || member.isMetaAnnotatedWith(it) }
        }

    private fun isConfiguration(type: JavaClass): Boolean = type.isAnnotatedWith(CONFIGURATION) || type.isMetaAnnotatedWith(CONFIGURATION)

    private fun hasConfigurationRole(type: JavaClass): Boolean =
        (within(type, "config") && type.simpleName.endsWith("Config")) ||
            (type.name == "$root.TemplateApplication" && type.isAnnotatedWith(SPRING_BOOT_APPLICATION))

    private fun within(
        type: JavaClass,
        layer: String,
    ): Boolean = type.packageName == "$root.$layer" || type.packageName.startsWith("$root.$layer.")

    private fun isIoType(type: JavaClass): Boolean =
        type.name != "java.io.Serializable" && ioPackages.any { type.packageName.startsWith(it) }

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

    companion object {
        private const val CONTROLLER = "org.springframework.stereotype.Controller"
        private const val CONFIGURATION = "org.springframework.context.annotation.Configuration"
        private const val SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication"
        private val injectionAnnotations =
            listOf(
                "org.springframework.beans.factory.annotation.Autowired",
                "jakarta.inject.Inject",
                "javax.inject.Inject",
                "jakarta.annotation.Resource",
                "javax.annotation.Resource",
                "org.springframework.beans.factory.annotation.Value",
            )
        private val ioPackages =
            listOf("java.io", "java.sql", "javax.sql", "java.net", "java.nio.file", "java.nio.channels", "kotlin.io")
    }
}
