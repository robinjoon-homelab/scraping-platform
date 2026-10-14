package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.domain.JavaConstructor
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class DddArchitectureRulesTest {
    @TestFactory
    fun `valid aggregates pass the generic and DDD rules in feature-first and layer-first layouts`(): List<DynamicTest> =
        (ArchitectureRules(GOOD).all() + goodRules.all()).map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(goodClasses) }
        }

    @TestFactory
    fun `each invalid fixture violates its DDD rule on its own`(): List<DynamicTest> =
        violations().flatMap { (rule, names) -> names.map { rejectedFixture(rule, it) } }

    @Test
    fun `only marked non-annotation classes are aggregate roots`() {
        assertThat(goodClasses.filter(goodRules::isAggregateRoot).map { it.simpleName })
            .containsExactlyInAnyOrder("Product", "Review", "Order", "Invoice")
    }

    @Test
    fun `good fixtures contain the Kotlin generated members the root rules must accept`() {
        val order = goodClasses.get("$GOOD.billing.orders.domain.order.Order")
        val copies = order.methods.filter { it.name.startsWith("copy-") }
        assertThat(copies).anyMatch { JavaModifier.PRIVATE in it.modifiers && JavaModifier.SYNTHETIC !in it.modifiers }
        assertThat(copies).anyMatch { JavaModifier.SYNTHETIC in it.modifiers && JavaModifier.PRIVATE !in it.modifiers }
        assertThat(order.constructors).anyMatch { JavaModifier.SYNTHETIC in it.modifiers && it.hasConstructorMarker() }

        val invoice = goodClasses.get("$GOOD.billing.orders.domain.invoice.Invoice")
        assertThat(invoice.constructors).anyMatch {
            JavaModifier.SYNTHETIC in it.modifiers && JavaModifier.PUBLIC in it.modifiers && it.hasConstructorMarker()
        }
    }

    @Test
    fun `an exposed Kotlin copy fails only the copy rule`() {
        val subject = fixture("ExposedCopyRoot")
        assertThat(subject.single().methods).anyMatch {
            it.name.startsWith("copy-") && JavaModifier.PUBLIC in it.modifiers && JavaModifier.SYNTHETIC !in it.modifiers
        }
        assertThat(badRules.privateRootConstructors.evaluate(subject).hasViolation()).isFalse()
        assertThat(badRules.privateRootCopies.evaluate(subject).hasViolation()).isTrue()
    }

    @Test
    fun `a subpackage of an aggregate package belongs to that aggregate and may hold its root`() {
        val history = goodClasses.get("$GOOD.billing.orders.domain.order.history.OrderHistory")

        assertThat(history.directDependenciesFromSelf.map { it.targetClass.name }).contains("$GOOD.billing.orders.domain.order.Order")
        assertThat(goodRules.aggregateRootReferences.evaluate(goodClasses).hasViolation()).isFalse()
    }

    @Test
    fun `generic rules still allow what the supplemental DDD rules reject`() {
        val genericRules = ArchitectureRules(BAD)
        listOf("ForeignDomainValueUser", "SharedRootForeignUser").forEach { name ->
            assertThat(genericRules.featureEncapsulation.evaluate(fixture(name)).hasViolation()).describedAs(name).isFalse()
        }
    }

    private fun rejectedFixture(
        rule: ArchRule,
        name: String,
    ): DynamicTest =
        DynamicTest.dynamicTest("$name: ${rule.description}") {
            val subject = fixture(name)
            assertThat(subject).hasSize(1)
            assertThat(badRules.all().map { it.description }).contains(rule.description)
            assertThat(rule.allowEmptyShould(true).evaluate(subject).hasViolation())
                .describedAs("$name must violate ${rule.description}")
                .isTrue()
        }

    private fun violations(): List<Pair<ArchRule, List<String>>> =
        listOf(
            badRules.privateFeatureDomains to listOf("ForeignDomainValueUser", "ForeignDomainHolder", "ForeignRootUser"),
            badRules.aggregateRootReferences to
                listOf(
                    "DirectRootReference",
                    "NestedRootHolder",
                    "GenericRootReference",
                    "SharedRootReference",
                    "ForeignRootUser",
                    "SharedRootForeignUser",
                ),
            badRules.aggregateRootLocations to listOf("OutsideDomainRoot", "FeatureDomainRoot", "NestedAggregateRoot"),
            badRules.privateRootConstructors to listOf("PublicConstructorRoot"),
            badRules.privateRootCopies to listOf("ExposedCopyRoot"),
            badRules.immutableAggregateRoots to listOf("MutableRoot"),
            badRules.singleRootPerPackage to listOf("FirstDualRoot", "SecondDualRoot"),
            badRules.singleRootRepositories to listOf("MixedRepository", "IndirectMixedRepository", "InheritedMixedRepository"),
            badRules.singleRepositoryPerRoot to listOf("DoubledRoot"),
        )

    private fun fixture(name: String): JavaClasses =
        badClasses.that(DescribedPredicate.describe<JavaClass>("have simple name $name") { it.simpleName == name })

    private fun JavaConstructor.hasConstructorMarker(): Boolean = rawParameterTypes.any { it.name == CONSTRUCTOR_MARKER }

    companion object {
        private const val FIXTURES = "architecturefixtures.ddd"
        private const val GOOD = "$FIXTURES.good"
        private const val BAD = "$FIXTURES.bad"
        private const val CONSTRUCTOR_MARKER = "kotlin.jvm.internal.DefaultConstructorMarker"
        private val goodRules = DddArchitectureRules(GOOD)
        private val badRules = DddArchitectureRules(BAD)
        private val goodClasses = ClassFileImporter().importPackages(GOOD)
        private val badClasses = ClassFileImporter().importPackages(BAD)
    }
}
