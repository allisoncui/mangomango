package com.mangomango.ledger.common;

/** Constants and checks for identifying the calling client program. */
public final class ClientHeader {

  /** Header every request carries to identify the calling client. */
  public static final String NAME = "X-Client-Id";

  private ClientHeader() {}

  /**
   * Returns the client ID with surrounding whitespace removed, so {@code " demo "} and
   * {@code "demo"} are the same client.
   *
   * @throws ValidationException if the header value is null or blank
   */
  public static String require(String clientId) {
    if (clientId == null || clientId.isBlank()) {
      throw new ValidationException(NAME + " header must not be blank");
    }
    return clientId.trim();
  }
}
