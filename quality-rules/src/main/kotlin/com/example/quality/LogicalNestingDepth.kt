package com.example.quality

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhenExpression

class LogicalNestingDepth(
    config: Config,
) : Rule(config, "Keep control flow shallow, including branches inside arbitrary DSL lambdas."),
    RequiresAnalysisApi {
    @Configuration("Maximum nesting of if, when, loops and standard collection/sequence forEach calls.")
    private val allowedDepth: Int by config(defaultValue = 2)

    private var depth = 0

    override fun visitNamedFunction(function: KtNamedFunction) {
        val enclosingDepth = depth
        depth = 0
        super.visitNamedFunction(function)
        depth = enclosingDepth
    }

    override fun visitIfExpression(expression: KtIfExpression) {
        if (expression.isElseIf()) {
            super.visitIfExpression(expression)
        } else {
            nested(expression) { super.visitIfExpression(expression) }
        }
    }

    override fun visitLoopExpression(loopExpression: KtLoopExpression) {
        nested(loopExpression) { super.visitLoopExpression(loopExpression) }
    }

    override fun visitWhenExpression(expression: KtWhenExpression) {
        nested(expression) { super.visitWhenExpression(expression) }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        val iterative =
            analyze(expression) {
                val callable =
                    expression
                        .resolveToCall()
                        ?.successfulFunctionCallOrNull()
                        ?.symbol
                        ?.callableId
                callable?.asSingleFqName()?.asString() in iterationFunctions
            }
        if (iterative) {
            nested(expression) { super.visitCallExpression(expression) }
        } else {
            super.visitCallExpression(expression)
        }
    }

    private fun nested(
        expression: KtExpression,
        visitChildren: () -> Unit,
    ) {
        depth++
        if (depth == allowedDepth + 1) {
            report(
                Finding(
                    Entity.from(expression),
                    "Control flow exceeds $allowedDepth levels. Extract a named operation or use a guard clause.",
                ),
            )
        }
        visitChildren()
        depth--
    }

    private fun KtIfExpression.isElseIf(): Boolean {
        val container = parent as? KtContainerNodeForControlStructureBody ?: return false
        val enclosingIf = container.parent as? KtIfExpression ?: return false
        return enclosingIf.`else` == this
    }

    private companion object {
        val iterationFunctions =
            setOf(
                "kotlin.collections.forEach",
                "kotlin.collections.forEachIndexed",
                "kotlin.sequences.forEach",
                "kotlin.sequences.forEachIndexed",
            )
    }
}
