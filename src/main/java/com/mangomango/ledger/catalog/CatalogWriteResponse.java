package com.mangomango.ledger.catalog;

/**
 * Result of storing a new pricing version.
 *
 * @param stored the version just stored
 * @param active the version currently in effect, or null if every version starts in the future
 */
public record CatalogWriteResponse(CatalogEntryResponse stored, CatalogEntryResponse active) {}
