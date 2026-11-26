package it.polito.wa2.catalogue.adapters.inbound.web.common

import it.polito.wa2.catalogue.application.exceptions.IdempotencyKeyInProgressException
import it.polito.wa2.catalogue.application.exceptions.IdempotencyKeyReusedException
import it.polito.wa2.catalogue.application.exceptions.MissingOrInvalidIdempotencyKeyException
import it.polito.wa2.catalogue.domain.events.EventNotPublishedException
import it.polito.wa2.catalogue.domain.sessions.ConcurrentModificationException
import it.polito.wa2.catalogue.domain.sessions.InvalidSessionStateTransitionException
import it.polito.wa2.catalogue.domain.sessions.SessionNotFoundException
import it.polito.wa2.catalogue.domain.sessions.SessionValidationException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

/**
 * Single mapping point (L13) turning every failure into an RFC 9457 problem document with a stable `code`.
 * Status code, problem type and log level are decided once per failure class, never in controllers.
 * Framework failures (binding, unsupported method, unreadable body, unknown route, ...) are handled by
 * [ResponseEntityExceptionHandler] and enriched with the same members.
 */
@RestControllerAdvice
class ProblemDetailsHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(ProblemDetailsHandler::class.java)

    /** A failure class: its status, documented type, title, stable code and log level. */
    private data class ProblemClass(
        val status: HttpStatus,
        val type: String,
        val title: String,
        val code: String,
        val level: Level
    )

    // ---- client-owned failures -------------------------------------------------------------

    @ExceptionHandler(SessionNotFoundException::class)
    fun sessionNotFound(ex: SessionNotFoundException, request: HttpServletRequest) =
        problem(SESSION_NOT_FOUND, ex, request)

    @ExceptionHandler(UnsupportedApiVersionException::class)
    fun unsupportedVersion(ex: UnsupportedApiVersionException, request: HttpServletRequest) =
        problem(UNSUPPORTED_API_VERSION, ex, request)

    @ExceptionHandler(MissingOrInvalidIdempotencyKeyException::class)
    fun invalidIdempotencyKey(ex: MissingOrInvalidIdempotencyKeyException, request: HttpServletRequest) =
        problem(INVALID_IDEMPOTENCY_KEY, ex, request)

    @ExceptionHandler(InvalidRequestParameterException::class)
    fun invalidParameter(ex: InvalidRequestParameterException, request: HttpServletRequest) =
        problem(INVALID_PARAMETER, ex, request)

    @ExceptionHandler(SessionValidationException::class)
    fun validationFailed(ex: SessionValidationException, request: HttpServletRequest) =
        problem(VALIDATION_FAILED, ex, request)

    @ExceptionHandler(IdempotencyKeyReusedException::class)
    fun idempotencyKeyReused(ex: IdempotencyKeyReusedException, request: HttpServletRequest) =
        problem(IDEMPOTENCY_KEY_REUSED, ex, request)

    @ExceptionHandler(MissingPreconditionException::class)
    fun preconditionRequired(ex: MissingPreconditionException, request: HttpServletRequest) =
        problem(PRECONDITION_REQUIRED, ex, request)

    // ---- contention ------------------------------------------------------------------------

    @ExceptionHandler(InvalidSessionStateTransitionException::class)
    fun invalidTransition(ex: InvalidSessionStateTransitionException, request: HttpServletRequest) =
        problem(INVALID_STATE_TRANSITION, ex, request)

    @ExceptionHandler(EventNotPublishedException::class)
    fun eventNotPublished(ex: EventNotPublishedException, request: HttpServletRequest) =
        problem(EVENT_NOT_PUBLISHED, ex, request)

    @ExceptionHandler(IdempotencyKeyInProgressException::class)
    fun idempotencyKeyInProgress(ex: IdempotencyKeyInProgressException, request: HttpServletRequest) =
        problem(IDEMPOTENCY_KEY_IN_PROGRESS, ex, request)

    @ExceptionHandler(ConcurrentModificationException::class)
    fun preconditionFailed(ex: ConcurrentModificationException, request: HttpServletRequest) =
        problem(PRECONDITION_FAILED, ex, request)

    // ---- our bugs ----------------------------------------------------------------------------

    /**
     * Catches whatever no specific handler claimed: full exception in the log (with the correlation id
     * from the MDC), only the class outside.
     */
    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
        return problem(INTERNAL_ERROR, request = request, detail = "An unexpected internal error occurred")
    }

    /**
     * Enriches the problem documents built by the framework for its own failures with the same
     * `type`/`code` conventions, so the client always parses a single format.
     */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val problem = (body as? ProblemDetail) ?: ProblemDetail.forStatus(statusCode)
        if (problem.properties?.containsKey(CODE) != true) {
            val slug = HttpStatus.resolve(statusCode.value())?.name?.lowercase() ?: "http_${statusCode.value()}"
            problem.type = problemType(slug.replace('_', '-'))
            problem.setProperty(CODE, slug)
        }
        log.debug("Framework rejected request with {}: {}", statusCode.value(), ex.message)
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    private fun problem(
        problemClass: ProblemClass,
        ex: Exception? = null,
        request: HttpServletRequest,
        detail: String = ex?.message ?: problemClass.title
    ): ResponseEntity<ProblemDetail> {
        if (ex != null) {
            log.atLevel(problemClass.level)
                .log("{} on {} {}: {}", problemClass.code, request.method, request.requestURI, detail)
        }
        val body = ProblemDetail.forStatusAndDetail(problemClass.status, detail).apply {
            type = problemType(problemClass.type)
            title = problemClass.title
            instance = URI.create(request.requestURI)
            setProperty(CODE, problemClass.code)
        }
        return ResponseEntity
            .status(problemClass.status)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body)
    }

    private fun problemType(slug: String) = URI.create("$PROBLEM_BASE_URI/$slug")

    private companion object {
        const val PROBLEM_BASE_URI = "https://api.example.com/problems"
        const val CODE = "code"

        val SESSION_NOT_FOUND = ProblemClass(HttpStatus.NOT_FOUND, "session-not-found", "Session not found", "session_not_found", Level.DEBUG)
        val UNSUPPORTED_API_VERSION = ProblemClass(HttpStatus.BAD_REQUEST, "unsupported-api-version", "Unsupported API version", "unsupported_api_version", Level.DEBUG)
        val INVALID_IDEMPOTENCY_KEY = ProblemClass(HttpStatus.BAD_REQUEST, "invalid-idempotency-key", "Invalid Idempotency-Key", "invalid_idempotency_key", Level.DEBUG)
        val INVALID_PARAMETER = ProblemClass(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid request parameter", "invalid_parameter", Level.DEBUG)
        val VALIDATION_FAILED = ProblemClass(HttpStatus.UNPROCESSABLE_ENTITY, "validation-failed", "Validation failed", "validation_failed", Level.DEBUG)
        val IDEMPOTENCY_KEY_REUSED = ProblemClass(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reused", "Idempotency-Key reused", "idempotency_key_reused", Level.DEBUG)
        val PRECONDITION_REQUIRED = ProblemClass(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "Precondition required", "precondition_required", Level.DEBUG)
        val INVALID_STATE_TRANSITION = ProblemClass(HttpStatus.CONFLICT, "invalid-state-transition", "Invalid session state transition", "invalid_state_transition", Level.INFO)
        val EVENT_NOT_PUBLISHED = ProblemClass(HttpStatus.CONFLICT, "event-not-published", "Event not published", "event_not_published", Level.INFO)
        val IDEMPOTENCY_KEY_IN_PROGRESS = ProblemClass(HttpStatus.CONFLICT, "idempotency-key-in-progress", "Request in progress", "idempotency_key_in_progress", Level.INFO)
        val PRECONDITION_FAILED = ProblemClass(HttpStatus.PRECONDITION_FAILED, "precondition-failed", "Precondition failed", "precondition_failed", Level.INFO)
        val INTERNAL_ERROR = ProblemClass(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error", "internal_error", Level.ERROR)
    }
}
