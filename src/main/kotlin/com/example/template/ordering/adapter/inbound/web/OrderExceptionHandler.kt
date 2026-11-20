package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OfferNotOnSaleException
import com.example.template.ordering.application.port.input.OrderNotFoundException
import com.example.template.ordering.domain.InvalidSkuException
import com.example.template.ordering.domain.order.InvalidOrderException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Applies only to OrderController and is consulted before any global advice, so another context's handler cannot
// turn ordering errors into its own responses. Extending ResponseEntityExceptionHandler also answers malformed
// requests to this controller with ProblemDetail without relying on another context's advice.
@RestControllerAdvice(assignableTypes = [OrderController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class OrderExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidOrderException::class, InvalidSkuException::class)
    fun handleInvalidOrder(exception: RuntimeException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "Invalid order", exception)

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(exception: OrderNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "Order not found", exception)

    // The request is well formed but conflicts with the current offers, so it is a conflict rather than a bad request.
    @ExceptionHandler(OfferNotOnSaleException::class)
    fun handleOfferNotOnSale(exception: OfferNotOnSaleException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Offer not on sale", exception)

    private fun problem(
        status: HttpStatus,
        summary: String,
        exception: RuntimeException,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, requireNotNull(exception.message)).apply {
            title = summary
        }
}
