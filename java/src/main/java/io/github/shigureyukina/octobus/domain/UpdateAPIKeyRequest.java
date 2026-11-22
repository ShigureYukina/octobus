package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateAPIKeyRequest(
    int id,
    String name,
    Boolean enabled,
    @JsonProperty("expire_at") Long expireAt,
    @JsonProperty("max_cost") Double maxCost,
    @JsonProperty("supported_models") String supportedModels
) {
}
