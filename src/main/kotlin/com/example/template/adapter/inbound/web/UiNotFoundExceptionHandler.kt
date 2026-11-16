package com.example.template.adapter.inbound.web

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.resource.NoResourceFoundException

private const val API_PATH_PREFIX = "/api/"

private val log = LoggerFactory.getLogger(UiNotFoundExceptionHandler::class.java)

// Declining via `produces` is not an option: text/html is compatible with */*, so API clients would receive HTML.
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UiNotFoundExceptionHandler {
    @ExceptionHandler(NoResourceFoundException::class)
    fun handle(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
    ): Any =
        if (acceptsHtmlExplicitly(request) && !request.requestURI.startsWith(API_PATH_PREFIX)) {
            ModelAndView("error/404", HttpStatus.NOT_FOUND)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.body)
        }

    // Escaping this handler would log a full WARN stack for every such request, and any client can send one.
    private fun acceptsHtmlExplicitly(request: HttpServletRequest): Boolean =
        try {
            request.getHeaders("Accept").toList().any { header ->
                MediaType.parseMediaTypes(header).any { it.equalsTypeAndSubtype(MediaType.TEXT_HTML) }
            }
        } catch (exception: InvalidMediaTypeException) {
            log.debug("Treating a malformed Accept header as non-HTML", exception)
            false
        }
}
