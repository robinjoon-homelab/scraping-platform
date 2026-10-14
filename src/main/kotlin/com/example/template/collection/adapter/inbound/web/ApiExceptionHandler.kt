package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidCollectionConfigurationException::class)
    fun handleInvalidConfiguration(exception: InvalidCollectionConfigurationException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, requireNotNull(exception.message)).apply {
            title = "Invalid collection configuration"
        }

    @ExceptionHandler(SourceNotFoundException::class)
    fun handleSourceNotFound(exception: SourceNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, requireNotNull(exception.message)).apply {
            title = "Source not found"
        }

    @ExceptionHandler(CollectionTaskNotFoundException::class)
    fun handleTaskNotFound(exception: CollectionTaskNotFoundException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, requireNotNull(exception.message)).apply {
            title = "Collection task not found"
        }

    @ExceptionHandler(StaleTaskVersionException::class)
    fun handleStaleVersion(exception: StaleTaskVersionException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, requireNotNull(exception.message)).apply {
            title = "Collection task version conflict"
        }
}
