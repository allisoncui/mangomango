package com.mangomango.ledger.recommendation;

import com.mangomango.ledger.common.ClientHeader;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operation 3: Evaluate Endpoint Routing &amp; Recommendation. */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

  private final RecommendationService recommendationService;

  /** Creates the controller. */
  public RecommendationController(RecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  /** Recommends a model for the described call. */
  @PostMapping
  public RecommendationResponse recommend(
      @RequestHeader(ClientHeader.NAME) String clientId,
      @Valid @RequestBody RecommendationRequest request) {
    return recommendationService.recommend(clientId, request);
  }
}
