package com.example.eventsbooking.web;

import com.example.eventsbooking.booking.BookingNotFoundException;
import com.example.eventsbooking.common.ConflictException;
import com.example.eventsbooking.event.EventNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    record FieldViolation(String field, String message) {
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("Request validation failed");
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Raised instead of {@link MethodArgumentNotValidException} when a controller method also validates other
     * parameters (a constrained header, say): then the body is validated through method validation too. The same
     * field list is produced, so clients see one error format.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            org.springframework.web.method.annotation.HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setTitle("Validation failed");
        problem.setDetail("Request validation failed");
        List<FieldViolation> errors = new java.util.ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof org.springframework.validation.method.ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(error -> errors.add(new FieldViolation(error.getField(), error.getDefaultMessage())));
            } else {
                org.springframework.web.bind.annotation.RequestHeader header =
                        result.getMethodParameter().getParameterAnnotation(
                                org.springframework.web.bind.annotation.RequestHeader.class);
                String name = header != null && !header.value().isEmpty() ? header.value()
                        : String.valueOf(result.getMethodParameter().getParameterName());
                result.getResolvableErrors()
                        .forEach(error -> errors.add(new FieldViolation(name, error.getDefaultMessage())));
            }
        });
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(EventNotFoundException.class)
    ProblemDetail handleEventNotFound(EventNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Event not found");
        return problem;
    }

    @ExceptionHandler(BookingNotFoundException.class)
    ProblemDetail handleBookingNotFound(BookingNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Booking not found");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle(ex.getTitle());
        return problem;
    }

    @ExceptionHandler(com.example.eventsbooking.common.TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(com.example.eventsbooking.common.TooManyRequestsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Too many requests");
        long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(com.example.eventsbooking.booking.InvalidSeatCountException.class)
    ProblemDetail handleInvalidSeatCount(com.example.eventsbooking.booking.InvalidSeatCountException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid seat count");
        return problem;
    }

    @ExceptionHandler(com.example.eventsbooking.user.InvalidVerificationTokenException.class)
    ProblemDetail handleInvalidVerificationToken(com.example.eventsbooking.user.InvalidVerificationTokenException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid confirmation token");
        return problem;
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(org.springframework.dao.OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was changed by someone else at the same time; reload it and try again");
        problem.setTitle("Concurrent modification");
        return problem;
    }

    /** Database (or its connection pool) is unavailable: a temporary condition, not a bug in the request. */
    @ExceptionHandler({org.springframework.transaction.CannotCreateTransactionException.class,
            org.springframework.dao.DataAccessResourceFailureException.class,
            org.springframework.dao.QueryTimeoutException.class})
    ResponseEntity<ProblemDetail> handleUnavailable(Exception ex) {
        log.error("Dependency unavailable", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable, try again in a few seconds");
        problem.setTitle("Service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "5")
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        String detail = ex instanceof BadCredentialsException
                ? "Invalid email or password"
                : "Authentication is required to access this resource";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
}
