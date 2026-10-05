package com.example.quality

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import dev.detekt.metrics.linesOfCode
import org.jetbrains.kotlin.psi.KtNamedFunction

class MaximumFunctionLength(
    config: Config,
) : Rule(config, "Even approved long test or DSL functions must fit within the absolute length limit.") {
    @Configuration("Absolute maximum body lines, including nested local functions but excluding blank/comment lines.")
    private val allowedLines: Int by config(defaultValue = 80)

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val body = function.bodyBlockExpression ?: function.bodyExpression ?: return
        val length = body.linesOfCode()
        if (length > allowedLines) {
            report(
                Finding(
                    Entity.from(function),
                    "${function.name} has $length body lines, exceeding the absolute limit of $allowedLines.",
                ),
            )
        }
    }
}
