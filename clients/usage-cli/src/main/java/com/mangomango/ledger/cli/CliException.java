package com.mangomango.ledger.cli;

/** An error reported to the user as a plain message rather than a stack trace. */
public class CliException extends RuntimeException {

  /**
   * Creates an exception.
   *
   * @param message text shown to the user
   */
  public CliException(String message) {
    super(message);
  }
}
