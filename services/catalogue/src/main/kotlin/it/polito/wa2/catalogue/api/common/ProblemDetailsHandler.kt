package it.polito.wa2.catalogue.api.common

import it.polito.wa2.catalogue.exceptions.SessionNotFoundException
import it.polito.wa2.catalogue.exceptions.UnsupportedApiVersionException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.net.URI

/**
 * Field-level validation error matching the OpenAPI `FieldError` schema.
 */
data class FieldErrorDetail(
    val field: String,
    val code: String,
    val detail: String
)

/**
 * Global exception handler translating domain and request errors into RFC 9457 Problem Details.
 */
@RestControllerAdvice
class ProblemDetailsHandler {

    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(
        ex: SessionNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.NOT_FOUND,
            type = "https://problems.event-ticketing.example/not-found",
            title = "Resource Not Found",
            code = "SESSION_NOT_FOUND",
            detail = ex.message ?: "Requested session does not exist",
            instance = request.requestURI
        )

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.NOT_FOUND,
            type = "https://problems.event-ticketing.example/not-found",
            title = "Resource Not Found",
            code = "NOT_FOUND",
            detail = ex.message ?: "Requested resource does not exist",
            instance = request.requestURI
        )

    @ExceptionHandler(UnsupportedApiVersionException::class)
    fun handleUnsupportedApiVersion(
        ex: UnsupportedApiVersionException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.BAD_REQUEST,
            type = "https://problems.event-ticketing.example/bad-request",
            title = "Bad Request",
            code = "UNSUPPORTED_API_VERSION",
            detail = ex.message ?: "Unsupported API version",
            instance = request.requestURI
        )

    @ExceptionHandler(
        IllegalArgumentException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
        ServletRequestBindingException::class
    )
    fun handleBadRequest(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.BAD_REQUEST,
            type = "https://problems.event-ticketing.example/bad-request",
            title = "Bad Request",
            code = "MALFORMED_REQUEST",
            detail = ex.message ?: "Malformed request",
            instance = request.requestURI
        )

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            type = "https://problems.event-ticketing.example/unsupported-media-type",
            title = "Unsupported Media Type",
            code = "UNSUPPORTED_MEDIA_TYPE",
            detail = ex.message ?: "Content-Type must be application/json",
            instance = request.requestURI
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.bindingResult.fieldErrors.map { err ->
            FieldErrorDetail(
                field = err.field,
                code = err.code ?: "INVALID_FIELD",
                detail = err.defaultMessage ?: "Invalid value"
            )
        }
        return problemResponse(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            type = "https://problems.event-ticketing.example/validation-failed",
            title = "Validation Failed",
            code = "VALIDATION_FAILED",
            detail = "Request validation failed",
            instance = request.requestURI,
            errors = fieldErrors
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.constraintViolations.map { v ->
            FieldErrorDetail(
                field = v.propertyPath.toString(),
                code = "CONSTRAINT_VIOLATION",
                detail = v.message
            )
        }
        return problemResponse(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            type = "https://problems.event-ticketing.example/validation-failed",
            title = "Validation Failed",
            code = "VALIDATION_FAILED",
            detail = "Request validation failed",
            instance = request.requestURI,
            errors = fieldErrors
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(
        ex: HandlerMethodValidationException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val fieldErrors = ex.parameterValidationResults.flatMap { result ->
            val paramName = result.methodParameter.parameterName ?: "parameter"
            result.resolvableErrors.map { err ->
                FieldErrorDetail(
                    field = paramName,
                    code = err.codes?.firstOrNull() ?: "INVALID_PARAMETER",
                    detail = err.defaultMessage ?: "Invalid value"
                )
            }
        }
        return problemResponse(
            status = HttpStatus.UNPROCESSABLE_ENTITY,
            type = "https://problems.event-ticketing.example/validation-failed",
            title = "Validation Failed",
            code = "VALIDATION_FAILED",
            detail = "Request validation failed",
            instance = request.requestURI,
            errors = fieldErrors
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> =
        problemResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            type = "https://problems.event-ticketing.example/internal-error",
            title = "Internal Server Error",
            code = "INTERNAL_ERROR",
            detail = "An unexpected error occurred",
            instance = request.requestURI
        )

    /**
     * Builds an `application/problem+json` response entity with all RFC 9457 fields
     * and the required `code` (and optional `errors`) extensions.
     */
    private fun problemResponse(
        status: HttpStatus,
        type: String,
        title: String,
        code: String,
        detail: String,
        instance: String,
        errors: List<FieldErrorDetail>? = null
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.type = URI.create(type)
            this.title = title
            this.instance = URI.create(instance)
            setProperty("code", code)
            if (!errors.isNullOrEmpty()) {
                setProperty("errors", errors)
            }
        }
        return ResponseEntity
            .status(status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem)
    }
}
