// Generated using Claude Code.

package com.mangomango.ledger.common;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps exceptions to consistent JSON error responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /** Business-rule validation failures. */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
    return build(HttpStatus.BAD_REQUEST, "validation_error", ex.getMessage(), List.of());
  }

  /** Missing resources. */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), List.of());
  }

  /** Operations the client may not perform. */
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
    return build(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage(), List.of());
  }

  /** Requests that contradict state the service already stored. */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorResponse> handleStateConflict(ConflictException ex) {
    return build(HttpStatus.CONFLICT, "conflict", ex.getMessage(), List.of());
  }

  /** Reports every invalid field in a request body. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
    List<String> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .sorted()
            .toList();
    return build(HttpStatus.BAD_REQUEST, "validation_error", "Invalid request body", details);
  }

  /** Two concurrent reports with the same idempotency key: the loser should simply retry. */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
    return build(HttpStatus.CONFLICT, "conflict",
        "Concurrent write conflict; retry the request", List.of());
  }

  /** Invalid path or query parameters. */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleInvalidParams(HandlerMethodValidationException ex) {
    return build(HttpStatus.BAD_REQUEST, "validation_error", "Invalid request parameters",
        List.of());
  }

  /** Unparseable bodies, missing headers and badly typed parameters. */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingRequestHeaderException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorResponse> handleMalformed(Exception ex) {
    return build(HttpStatus.BAD_REQUEST, "malformed_request", ex.getMessage(), List.of());
  }

  private static ResponseEntity<ErrorResponse> build(
      HttpStatus status, String code, String message, List<String> details) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message, details));
  }
}
