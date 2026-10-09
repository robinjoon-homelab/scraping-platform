package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ArchitectureLayoutsTest {
    @TestFactory
    fun `both layouts preserve valid nested roles shared contracts and internal helpers`(): List<DynamicTest> =
        ArchitectureRules(
            "$FIXTURES.good",
            additionalProductionTypes = setOf("$FIXTURES.good.migration.LegacyJdbcMigration"),
        ).all().map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(goodClasses) }
        }

    @TestFactory
    fun `nested packages cannot bypass role feature technology or transaction boundaries`(): List<DynamicTest> =
        violations().flatMap { (rule, names) -> names.map { rejectedFixture(rule, it) } }

    private fun rejectedFixture(
        rule: ArchRule,
        name: String,
    ): DynamicTest =
        DynamicTest.dynamicTest("$name: ${rule.description}") {
            val subject = badClasses.that(DescribedPredicate.describe<JavaClass>("have simple name $name") { it.simpleName == name })
            assertThat(subject).hasSize(1)
            assertThat(badRules.all().map { it.description }).contains(rule.description)
            assertThat(rule.allowEmptyShould(true).evaluate(subject).hasViolation())
                .describedAs("$name must violate ${rule.description}")
                .isTrue()
        }

    private fun violations(): List<Pair<ArchRule, List<String>>> =
        listOf(
            badRules.approvedLocations to listOf("MissingAdapterGroup", "UnlistedJdbcMigration"),
            badRules.repositoryRoles to
                listOf(
                    "WrongRole",
                    "SubstringRole",
                    "InterruptedRole",
                    "ReversedRole",
                    "OutputAtInbound",
                    "OutputAtConfig",
                    "ForeignFeature",
                    "SharedLeafFeature",
                ),
            badRules.inputPortImplementations to listOf("WrongServiceRole", "InputAtOutbound"),
            badRules.inboundPortRoles to listOf("WrongInputCaller", "ForeignInputCaller"),
            badRules.independentAdapters to listOf("StripeGateway", "FeatureLocalGateway", "ReverseAdapter"),
            badRules.featureEncapsulation to listOf("ForeignServiceCaller", "ForeignFeature"),
            badRules.pureDomain to listOf("FrameworkDomain"),
            badRules.noCoreIo to
                listOf("SocketDomain", "ProcessDomain", "RuntimeDomain", "ProcessHandleDomain", "RemoteDomain", "PreferencesDomain"),
            badRules.pureApplication to listOf("FrameworkPort"),
            badRules.independentPorts to listOf("ServiceDependentPort"),
            badRules.independentModels to listOf("ServiceDependentModel"),
            badRules.inboundBoundary to listOf("DirectServiceCaller"),
            badRules.outboundBoundary to listOf("CoreReverseDependency"),
            badRules.adapterTransactions to listOf("TransactionalScheduler", "TransactionalGateway", "ComposedGateway", "InheritedGateway"),
            badRules.adapterTransactionInfrastructure to
                listOf("ManualTransactions", "SpringTransactionManagerHolder", "SpringTransactionInterceptorHolder"),
            badRules.repositoryTransactionOwnership to listOf("ExposedTransactionStore"),
            badRules.adapterManualTransactions to
                listOf(
                    "JdbcCommit",
                    "JdbcRollback",
                    "JdbcAutoCommit",
                    "JdbcSavepoint",
                    "JdbcReleaseSavepoint",
                    "ExposedCommit",
                    "ExposedRollback",
                    "JdbcCommitReference",
                    "ExposedCommitReference",
                    "JdbcIsolation",
                    "JdbcReadOnly",
                    "ExposedConnectionCommit",
                    "ExposedConnectorCommit",
                    "ExposedConnectionCommitReference",
                    "ExposedConnectionRollback",
                    "ExposedConnectionAutoCommit",
                    "ExposedConnectionSavepoint",
                    "ExposedConnectionReleaseSavepoint",
                    "ExposedConnectionIsolation",
                    "ExposedConnectionReadOnly",
                ),
            badRules.isolatedExposed to listOf("JdbcLeak", "ExposedLeak", "UnlistedJdbcMigration", "ExposedMigration", "NotAMigration"),
        )

    companion object {
        private const val FIXTURES = "architecturefixtures.layouts"
        private val badRules =
            ArchitectureRules(
                "$FIXTURES.bad",
                additionalProductionTypes = setOf("$FIXTURES.bad.migration.ExposedMigration", "$FIXTURES.bad.migration.NotAMigration"),
            )
        private val goodClasses = ClassFileImporter().importPackages("$FIXTURES.good")
        private val badClasses = ClassFileImporter().importPackages("$FIXTURES.bad")
    }
}
