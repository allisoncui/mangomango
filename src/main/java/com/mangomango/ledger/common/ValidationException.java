// Generated using Claude Code.

package com.mangomango.ledger.common;

/** Thrown when a request is well-formed JSON but violates a business rule. */
public class ValidationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public ValidationException(String message) {
    super(message);
  }
}
