package com.mangomango.ledger.catalog;

import com.mangomango.ledger.common.ClientHeader;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operation 5: Manage Model Catalog &amp; Pricing. */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

  private final CatalogService catalogService;

  /** Creates the controller. */
  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  /** Stores a new pricing version (administrator clients only). */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CatalogWriteResponse addVersion(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @Valid @RequestBody CatalogEntryRequest request) {
    return catalogService.addVersion(clientId, request);
  }

  /** Lists the currently active version of every model. */
  @GetMapping
  public List<CatalogEntryResponse> listActive() {
    return catalogService.activeModels().stream().map(CatalogEntryResponse::from).toList();
  }

  /** Returns the currently active version of one model. */
  @GetMapping("/{provider}/{modelId}")
  public CatalogEntryResponse getActive(
      @PathVariable String provider, @PathVariable String modelId) {
    return CatalogEntryResponse.from(catalogService.activeModel(ModelKey.of(provider, modelId)));
  }

  /** Returns the full pricing history of one model, oldest first. */
  @GetMapping("/{provider}/{modelId}/history")
  public List<CatalogEntryResponse> getHistory(
      @PathVariable String provider, @PathVariable String modelId) {
    return catalogService.history(ModelKey.of(provider, modelId)).stream()
        .map(CatalogEntryResponse::from)
        .toList();
  }
}
