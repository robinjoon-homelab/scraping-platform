package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.Dependency
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaCodeUnit
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * Supplemental DDD guards that run next to [ArchitectureRules] and only narrow what the generic rules allow.
 *
 * An aggregate root is a non-annotation class directly annotated with `<root>.domain.AggregateRoot`. The marker is
 * an opt-in signal for these checks, not a security boundary: unmarked classes are not checked as roots and
 * reflection can still reach private members. Only a marked root designates an aggregate. The root resides directly
 * in `<feature>.domain.<aggregate>`, and that package with its subpackages belongs to the aggregate, so a helper
 * subpackage is never another aggregate while a sibling package is. `<feature>.domain` itself holds no root and keeps
 * values shared by the feature's aggregates. Bytecode dependencies cannot show every indirect heap reference: a
 * non-root wrapper that holds a root, a value passed through without a typed access, or a Kotlin value class erased
 * to its underlying type in a JVM signature is not reported as a reference to the wrapped type.
 */
internal class DddArchitectureRules(
    private val root: String,
) {
    private val packages = ArchitecturePackages(root)
    private val marker = "$root.domain.AggregateRoot"

    val privateFeatureDomains: ArchRule =
        classes().should(dependencies("not use another feature's domain", ::keepsForeignDomainPrivate))

    val aggregateRootReferences: ArchRule =
        classes().should(
            dependencies("reference aggregate roots only from their aggregate packages, own feature, or config", ::mayReferenceRoot),
        )

    val aggregateRootLocations: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(satisfy("reside directly in an aggregate package <feature>.domain.<aggregate>", ::isAggregatePackage))

    val privateRootConstructors: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(satisfy("keep every non-synthetic constructor private") { it.constructors.all(::isHiddenMember) })

    val privateRootCopies: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(satisfy("keep every non-synthetic copy method private") { it.methods.filter(::isCopyMethod).all(::isHiddenMember) })

    val immutableAggregateRoots: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(
                satisfy("keep every instance field final") { type ->
                    type.fields.all { JavaModifier.STATIC in it.modifiers || JavaModifier.FINAL in it.modifiers }
                },
            )

    val singleRootPerPackage: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(satisfy("be the only aggregate root in its package") { it.getPackage().classes.count(::isAggregateRoot) == 1 })

    // An output port interface that exposes a root acts as its repository whatever its role name. Ports without a root,
    // such as projection stores and generic bases, are unaffected, so both limits are at most one rather than exactly one.
    val singleRootRepositories: ArchRule =
        classes()
            .that(predicate("are output port interfaces", ::isOutputPortInterface))
            .should(satisfy("refer to at most one aggregate root in own, inherited, or generic types") { referencedRoots(it).size <= 1 })

    val singleRepositoryPerRoot: ArchRule =
        classes()
            .that(aggregateRoots())
            .should(satisfy("be exposed by at most one output port interface") { repositoryPorts(it).size <= 1 })

    fun all(): List<ArchRule> =
        listOf(
            privateFeatureDomains,
            aggregateRootReferences,
            aggregateRootLocations,
            privateRootConstructors,
            privateRootCopies,
            immutableAggregateRoots,
            singleRootPerPackage,
            singleRootRepositories,
            singleRepositoryPerRoot,
        ).map { it.allowEmptyShould(true) }

    fun isAggregateRoot(type: JavaClass): Boolean = !type.isAnnotation && type.isAnnotatedWith(marker)

    private fun keepsForeignDomainPrivate(dependency: Dependency): Boolean {
        val origin = location(dependency.originClass) ?: return true
        val target = location(dependency.targetClass) ?: return true
        if (target.layer != ArchitectureLayer.DOMAIN || target.feature.isEmpty()) return true
        return origin.feature == target.feature || origin.layer == ArchitectureLayer.CONFIG
    }

    private fun mayReferenceRoot(dependency: Dependency): Boolean {
        val target = dependency.targetClass
        if (!isAggregateRoot(target)) return true
        val origin = location(dependency.originClass) ?: return true
        return when (origin.layer) {
            ArchitectureLayer.CONFIG -> true
            ArchitectureLayer.DOMAIN -> belongsToPackage(dependency.originClass.packageName, target.packageName)
            else -> origin.feature == location(target)?.feature
        }
    }

    private fun isAggregatePackage(type: JavaClass): Boolean =
        location(type)?.let { it.layer == ArchitectureLayer.DOMAIN && it.path.size == 1 } == true

    // aggregateRootLocations keeps every root exactly one package below domain, so a subpackage of a root's package
    // can only belong to that root's aggregate.
    private fun belongsToPackage(
        packageName: String,
        aggregatePackage: String,
    ): Boolean = packageName == aggregatePackage || packageName.startsWith("$aggregatePackage.")

    // Kotlin keeps source visibility in metadata and javac ignores synthetic members. Default-argument
    // constructors, DefaultConstructorMarker bridges, and copy$default helpers are therefore exempt.
    private fun isHiddenMember(member: JavaCodeUnit): Boolean =
        JavaModifier.SYNTHETIC in member.modifiers || JavaModifier.PRIVATE in member.modifiers

    // Value class properties mangle the generated method to copy-<hash>.
    private fun isCopyMethod(member: JavaCodeUnit): Boolean = copyMethodName.matches(member.name)

    private fun referencedRoots(port: JavaClass): Set<JavaClass> =
        (port.allRawInterfaces + port)
            .flatMap { it.directDependenciesFromSelf }
            .map { it.targetClass }
            .filter(::isAggregateRoot)
            .toSet()

    private fun repositoryPorts(aggregateRoot: JavaClass): Set<JavaClass> =
        aggregateRoot.directDependenciesToSelf
            .map { it.originClass }
            .filter(::isOutputPortInterface)
            .flatMap { it.allSubclasses + it }
            .filter(::isOutputPortInterface)
            .toSet()

    private fun isOutputPortInterface(type: JavaClass): Boolean = type.isInterface && location(type)?.layer == ArchitectureLayer.OUTPUT_PORT

    private fun location(type: JavaClass): ArchitectureLocation? = packages.location(type.packageName)

    private fun aggregateRoots(): DescribedPredicate<JavaClass> = predicate("are aggregate roots marked with $marker", ::isAggregateRoot)

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

    private companion object {
        val copyMethodName = Regex("copy([-\$].*)?")
    }
}
