package com.mangomango.ledger.common;

/** Thrown when a requested resource does not exist for the calling client. */
public class NotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception. */
  public NotFoundException(String message) {
    super(message);
  }
}
