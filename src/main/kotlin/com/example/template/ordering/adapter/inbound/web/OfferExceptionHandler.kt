package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OfferNotFoundException
import com.example.template.ordering.domain.InvalidSkuException
import com.example.template.ordering.domain.offer.DuplicateOfferException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Scoped and ordered like OrderExceptionHandler, so offer errors and malformed offer requests never depend on
// another context's advice.
@RestControllerAdvice(assignableTypes = [OfferController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class OfferExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidSkuException::class)
    fun handleInvalidSku(exception: InvalidSkuException): ProblemDetail = problem(HttpStatus.BAD_REQUEST, "Invalid offer", exception)

    @ExceptionHandler(OfferNotFoundException::class)
    fun handleOfferNotFound(exception: OfferNotFoundException): ProblemDetail = problem(HttpStatus.NOT_FOUND, "Offer not found", exception)

    @ExceptionHandler(DuplicateOfferException::class)
    fun handleDuplicateOffer(exception: DuplicateOfferException): ProblemDetail =
        problem(HttpStatus.CONFLICT, "Offer already registered", exception)

    private fun problem(
        status: HttpStatus,
        summary: String,
        exception: RuntimeException,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, requireNotNull(exception.message)).apply {
            title = summary
        }
}
