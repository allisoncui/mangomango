package com.mangomango.ledger.common;

/** Constants for identifying the calling client program. */
public final class ClientHeader {

  /** Header every request carries to identify the calling client. */
  public static final String NAME = "X-Client-Id";

  private ClientHeader() {}
}
