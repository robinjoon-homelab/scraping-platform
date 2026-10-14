package com.example.template.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.Dependency
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.conditions.ArchConditions.onlyHaveDependenciesWhere
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

// Bounded contexts are explicit feature paths. A nested feature path belongs to the longest declared context it
// starts with, so a sub-feature cannot bypass the policy. Only a context's domain and application core are isolated
// here; its adapters, config, and undeclared features keep the generic feature and adapter rules.
// The rules read direct bytecode edges. They cannot see a call routed through a Kotlin function value or an
// invokedynamic lambda that config binds to another context's use case, nor reflection, inlined bodies, or a
// foreign value class that the compiler erases to its underlying type. Composition in config still needs review.
internal class BoundedContextArchitectureRules(
    root: String,
    contexts: Set<String>,
) {
    private val packages = ArchitecturePackages(root)
    private val contexts = contexts.map { it.split('.') }.sortedByDescending { it.size }

    val independentContextCores: ArchRule =
        dependencies("keep each declared context core independent of other features", ::keepsCoreIndependent)

    val independentSharedCode: ArchRule =
        dependencies("keep shared application and adapter code independent of feature types", ::keepsSharedCodeIndependent)

    fun all(): List<ArchRule> =
        listOf(independentContextCores, independentSharedCode)
            .map { it.allowEmptyShould(true) }

    // Root shared types are allowed. Features inside the same context remain under the generic and DDD rules.
    private fun keepsCoreIndependent(dependency: Dependency): Boolean {
        val origin = location(dependency.originClass) ?: return true
        val target = location(dependency.targetClass) ?: return true
        val context = contextOf(origin) ?: return true
        if (!(origin.isApplication || origin.layer == ArchitectureLayer.DOMAIN)) return true
        return target.feature.isEmpty() || contextOf(target) == context
    }

    // Root application and adapter code is shared by every context, so it may not name any feature.
    private fun keepsSharedCodeIndependent(dependency: Dependency): Boolean {
        val origin = location(dependency.originClass) ?: return true
        val target = location(dependency.targetClass) ?: return true
        if (origin.feature.isNotEmpty() || !(origin.isApplication || origin.isAdapter)) return true
        return target.feature.isEmpty()
    }

    private fun contextOf(position: ArchitectureLocation): List<String>? = contexts.firstOrNull { position.feature.take(it.size) == it }

    private fun location(type: JavaClass): ArchitectureLocation? = packages.location(type.packageName)

    private fun dependencies(
        description: String,
        test: (Dependency) -> Boolean,
    ): ArchRule = classes().should(onlyHaveDependenciesWhere(DescribedPredicate.describe(description, test)))
}
