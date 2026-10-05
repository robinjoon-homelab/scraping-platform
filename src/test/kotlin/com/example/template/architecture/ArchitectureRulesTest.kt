package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class ArchitectureRulesTest {
    private val badRules = ArchitectureRules("$FIXTURES.bad")

    @TestFactory
    fun `architecture rules reject intentional violations`(): List<DynamicTest> =
        negativeCases().map { (rule, expectedClasses) ->
            DynamicTest.dynamicTest(rule.description) {
                val result = rule.evaluate(badClasses)
                assertThat(result.hasViolation()).isTrue()
                assertThat(result.failureReport.details.joinToString("\n")).contains(*expectedClasses.toTypedArray())
            }
        }

    @TestFactory
    fun `valid constructor injection ports and pure types pass all rules`(): List<DynamicTest> =
        ArchitectureRules("$FIXTURES.good").all().map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(goodClasses) }
        }

    @Test
    fun `class outside the root package cannot escape location checks`() {
        val outsideClasses = ClassFileImporter().importPackages("$FIXTURES.outside")
        val result = badRules.approvedLocations.evaluate(outsideClasses)
        assertThat(result.hasViolation()).isTrue()
        assertThat(result.failureReport.details.joinToString()).contains("EscapedClass")
    }

    @Test
    fun `positive fixtures include Kotlin generated helpers and data contracts`() {
        assertThat(goodClasses.map { it.name })
            .contains(
                "$FIXTURES.good.application.port.input.CompanionPort\$Companion",
                "$FIXTURES.good.application.port.input.CreateCommand",
                "$FIXTURES.good.application.port.input.CreateResult",
            )
    }

    @Test
    fun `a disconnected domain cycle is rejected among otherwise acyclic packages`() {
        val domainClasses =
            badClasses.that(
                DescribedPredicate.describe("domain fixtures") { it.packageName.startsWith("$FIXTURES.bad.domain") },
            )
        assertThat(domainClasses.map { it.simpleName }).contains("Alpha", "Beta", "IoDomain", "ApplicationDependency")

        val result = badRules.acyclicPackages.evaluate(domainClasses)
        assertThat(result.hasViolation()).isTrue()
        assertThat(result.failureReport.details.joinToString("\n")).contains("Alpha", "Beta")
    }

    private fun negativeCases(): List<Pair<ArchRule, List<String>>> =
        listOf(
            badRules.approvedLocations to listOf("UnassignedClass", "HelperKt"),
            badRules.pureDomain to listOf("ApplicationDependency", "MisplacedController"),
            badRules.pureApplication to listOf("BadService"),
            badRules.noCoreIo to listOf("IoDomain", "BadService", "java.io.File", "java.net.Socket", "java.sql.Connection"),
            badRules.independentPorts to listOf("BadInput", "BadOutput"),
            badRules.inboundBoundary to listOf("BadController"),
            badRules.outboundBoundary to listOf("BadPersistence"),
            badRules.portContracts to listOf("ConcreteUseCase", "ConcreteRepository"),
            badRules.constructorInjection to
                listOf("FieldInjected", "MethodInjected", "ResourceFieldInjected", "ResourceMethodInjected", "ValueFieldInjected"),
            badRules.controllerRoles to listOf("MisplacedController", "WrongEndpoint", "ComposedEndpoint", "MvcEndpoint"),
            badRules.controllerNames to listOf("MisplacedController"),
            badRules.configurationRoles to listOf("MisplacedConfig", "WrongConfiguration"),
            badRules.repositoryRoles to listOf("MisplacedRepository", "MisplacedStore"),
            badRules.isolatedExposed to listOf("ExposedResponse", "GenericExposedResponse"),
            badRules.acyclicPackages to listOf("BadController", "BadPersistence"),
        )

    companion object {
        private const val FIXTURES = "architecturefixtures"
        private val badClasses = ClassFileImporter().importPackages("$FIXTURES.bad")
        private val goodClasses = ClassFileImporter().importPackages("$FIXTURES.good")
    }
}
