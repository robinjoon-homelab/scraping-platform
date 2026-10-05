package com.example.quality

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaIntersectionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeArgumentWithVariance
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

class NoMutableCollectionExposure(
    config: Config,
) : Rule(config, "Public APIs must expose read-only collection contracts."),
    RequiresAnalysisApi {
    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        analyze(function) {
            checkContract(function, function.symbol)
        }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        analyze(property) {
            checkContract(property, property.symbol)
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        if (parameter.parent?.parent is KtFunctionType) return
        analyze(parameter) {
            val symbol = parameter.symbol as? KaValueParameterSymbol ?: return
            val property = symbol.generatedPrimaryConstructorProperty
            val exposedInput = symbol.containingDeclaration?.let { isExposed(it) } == true
            val exposedProperty = property?.let { isExposed(it) } == true
            if ((exposedInput || exposedProperty) && containsMutableCollection(symbol.returnType)) {
                reportExposure(parameter)
            }
        }
    }

    override fun visitTypeAlias(typeAlias: KtTypeAlias) {
        super.visitTypeAlias(typeAlias)
        analyze(typeAlias) {
            val symbol = typeAlias.symbol
            if (isExposed(symbol) && containsMutableCollection(symbol.expandedType)) {
                reportExposure(typeAlias)
            }
        }
    }

    override fun visitClassOrObject(classOrObject: KtClassOrObject) {
        super.visitClassOrObject(classOrObject)
        analyze(classOrObject) {
            val symbol = classOrObject.classSymbol ?: return
            if (isExposed(symbol) && containsMutableCollection(symbol.defaultType)) {
                reportExposure(classOrObject)
            }
        }
    }

    private fun KaSession.checkContract(
        declaration: KtNamedDeclaration,
        symbol: KaCallableSymbol,
    ) {
        if (!isExposed(symbol)) return
        val types = listOfNotNull(symbol.returnType, symbol.receiverParameter?.returnType)
        if (types.any { containsMutableCollection(it) }) {
            reportExposure(declaration)
        }
    }

    private fun KaSession.isExposed(symbol: KaDeclarationSymbol): Boolean {
        val visible = symbol.visibility == KaSymbolVisibility.PUBLIC || symbol.visibility == KaSymbolVisibility.PROTECTED
        return visible && (symbol.containingDeclaration?.let { isExposed(it) } ?: true)
    }

    private fun KaSession.containsMutableCollection(
        type: KaType,
        visited: MutableSet<KaType> = mutableSetOf(),
    ): Boolean {
        val expanded = type.fullyExpandedType
        if (!visited.add(expanded) || expanded is KaErrorType || expanded.isNothingType) return false
        if (mutableCollections.any { expanded.withNullability(false).isSubtypeOf(it) }) return true
        val nested = nestedTypes(expanded).asSequence() + expanded.directSupertypes
        return nested.any { containsMutableCollection(it, visited) }
    }

    private fun nestedTypes(type: KaType): List<KaType> =
        when (type) {
            is KaClassType -> type.typeArguments.filterIsInstance<KaTypeArgumentWithVariance>().map { it.type }
            is KaFlexibleType -> listOf(type.lowerBound, type.upperBound)
            is KaDefinitelyNotNullType -> listOf(type.original)
            is KaIntersectionType -> type.conjuncts
            is KaTypeParameterType -> type.symbol.upperBounds
            else -> emptyList()
        }

    private fun reportExposure(declaration: KtNamedDeclaration) {
        report(
            Finding(
                Entity.from(declaration),
                "${declaration.name} exposes a mutable collection. Use List, Set or Map in public contracts.",
            ),
        )
    }

    private companion object {
        val mutableCollections =
            listOf("MutableCollection", "MutableList", "MutableSet", "MutableMap")
                .map { ClassId.topLevel(FqName("kotlin.collections.$it")) }
    }
}
