package com.example.template.inventory.adapter.inbound.web

import com.example.template.inventory.application.port.input.StockItemNotFoundException
import com.example.template.inventory.domain.stock.DuplicateSkuException
import com.example.template.inventory.domain.stock.InvalidStockException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Applies only to StockController and is consulted before any global advice, so another feature's handler cannot
// turn inventory errors into its own responses. It also extends ResponseEntityExceptionHandler, so malformed
// requests to this controller answer with ProblemDetail without relying on another feature's advice.
@RestControllerAdvice(assignableTypes = [StockController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class StockExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidStockException::class)
    fun handleInvalidStock(exception: InvalidStockException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Invalid stock request", exception)

    @ExceptionHandler(StockItemNotFoundException::class)
    fun handleStockItemNotFound(exception: StockItemNotFoundException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Stock item not found", exception)

    @ExceptionHandler(DuplicateSkuException::class)
    fun handleDuplicateSku(exception: DuplicateSkuException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "SKU already registered", exception)

    private fun problem(
        status: HttpStatus,
        summary: String,
        exception: RuntimeException,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, requireNotNull(exception.message)).apply {
            title = summary
        }
}
