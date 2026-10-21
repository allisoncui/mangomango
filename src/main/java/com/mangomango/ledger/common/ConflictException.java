package com.mangomango.ledger.common;

/** Thrown when a request conflicts with state the service has already stored. */
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public ConflictException(String message) {
    super(message);
  }
}
