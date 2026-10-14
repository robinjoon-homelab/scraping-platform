package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

// The context fixtures run the generic, DDD, and context rules together, as production does. Ordering holds two
// aggregates, inventory is an independent context, and root code is shared by both.
class BoundedContextArchitectureRulesTest {
    @TestFactory
    fun `two aggregates of one context and an independent context satisfy every installed rule`(): List<DynamicTest> =
        goodInstalled.map { rule ->
            DynamicTest.dynamicTest(rule.description) { rule.check(goodClasses) }
        }

    @TestFactory
    fun `each forbidden dependency breaks exactly its own rules`(): List<DynamicTest> =
        listOf(
            rejected("ForeignUseCaseInCore", badContextRules.independentContextCores),
            rejected("ForeignModelInCore", badContextRules.independentContextCores),
            rejected("ForeignDomainInCore", badContextRules.independentContextCores, badDddRules.privateFeatureDomains),
            rejected("ForeignContextIdInCore", badContextRules.independentContextCores, badDddRules.privateFeatureDomains),
            rejected("NestedForeignUseCase", badContextRules.independentContextCores),
            rejected("BusinessAwareService", badContextRules.independentSharedCode),
            rejected("BusinessAwareStore", badContextRules.independentSharedCode),
            rejected("CartHoldingOrder", badDddRules.aggregateRootReferences),
            rejected("RootAtContextDomain", badDddRules.aggregateRootLocations),
            rejected("RootNestedInAggregate", badDddRules.aggregateRootLocations),
        )

    // The good rules pass vacuously if the edges they allow are missing, so the allowed references are asserted too.
    @Test
    fun `aggregates of one context refer to each other by ordinary ids that remain in bytecode`() {
        assertThat(goodClasses.filter(goodDddRules::isAggregateRoot).map { it.name })
            .containsExactlyInAnyOrder("$ORDERING_DOMAIN.order.Order", "$ORDERING_DOMAIN.cart.Cart")
        assertThat(dependenciesOf("$ORDERING_DOMAIN.order.Order"))
            .contains("$ORDERING_DOMAIN.cart.CartId")
            .doesNotContain("$ORDERING_DOMAIN.cart.Cart")
        assertThat(dependenciesOf("$ORDERING_DOMAIN.order.history.OrderHistory")).contains("$ORDERING_DOMAIN.order.Order")
    }

    @Test
    fun `the context's own service uses both aggregates through their repositories`() {
        assertThat(dependenciesOf("$GOOD.ordering.application.service.OrderService")).contains(
            "$GOOD.ordering.application.port.output.OrderRepository",
            "$GOOD.ordering.application.port.output.CartRepository",
            "$ORDERING_DOMAIN.order.Order",
            "$ORDERING_DOMAIN.cart.Cart",
        )
    }

    @Test
    fun `context policy does not silently apply to undeclared features`() {
        val rules = BoundedContextArchitectureRules(BAD, setOf("ordering"))

        assertThat(rules.independentContextCores.evaluate(fixture("ForeignUseCaseInCore")).hasViolation()).isFalse()
    }

    @Test
    fun `root composition and feature-independent shared code remain allowed`() {
        assertThat(goodClasses.map { it.simpleName })
            .contains("ModuleConfig", "OrderService", "StockService", "Quantity", "IdGenerator", "SequenceIdGenerator")
        goodContextRules.all().forEach { rule -> assertThat(rule.evaluate(goodClasses).hasViolation()).isFalse() }
    }

    @Test
    fun `a nested feature path belongs to its declared context`() {
        assertThat(goodClasses.map { it.simpleName }).contains("ReturnPolicy")
        assertThat(goodContextRules.independentContextCores.evaluate(goodClasses).hasViolation()).isFalse()
        assertThat(badContextRules.independentContextCores.evaluate(fixture("NestedForeignUseCase")).hasViolation()).isTrue()
    }

    private fun rejected(
        name: String,
        vararg expected: ArchRule,
    ): DynamicTest =
        DynamicTest.dynamicTest("$name: ${expected.joinToString { it.description }}") {
            val subject = fixture(name)
            assertThat(subject).hasSize(1)
            assertThat(badInstalled.filter { it.evaluate(subject).hasViolation() }.map { it.description })
                .containsExactlyInAnyOrderElementsOf(expected.map { it.description })
        }

    private fun dependenciesOf(name: String): Set<String> =
        goodClasses
            .get(name)
            .directDependenciesFromSelf
            .map { it.targetClass.name }
            .toSet()

    private fun fixture(name: String) =
        badClasses.that(DescribedPredicate.describe<JavaClass>("have simple name $name") { it.simpleName == name })

    companion object {
        private const val GOOD = "architecturefixtures.contexts.good"
        private const val BAD = "architecturefixtures.contexts.bad"
        private const val ORDERING_DOMAIN = "$GOOD.ordering.domain"
        private val contexts = setOf("ordering", "inventory")
        private val goodContextRules = BoundedContextArchitectureRules(GOOD, contexts)
        private val badContextRules = BoundedContextArchitectureRules(BAD, contexts)
        private val goodDddRules = DddArchitectureRules(GOOD)
        private val badDddRules = DddArchitectureRules(BAD)
        private val goodInstalled = ArchitectureRules(GOOD).all() + goodDddRules.all() + goodContextRules.all()
        private val badInstalled = ArchitectureRules(BAD).all() + badDddRules.all() + badContextRules.all()
        private val goodClasses = ClassFileImporter().importPackages(GOOD)
        private val badClasses = ClassFileImporter().importPackages(BAD)
    }
}
