// Generated using Claude Code.

package com.mangomango.ledger.common;

/** Thrown when a client calls an operation it is not permitted to perform. */
public class ForbiddenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public ForbiddenException(String message) {
    super(message);
  }
}
