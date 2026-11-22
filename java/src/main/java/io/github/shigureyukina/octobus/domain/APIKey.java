package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record APIKey(
    int id,
    String name,
    @JsonProperty("api_key") String apiKey,
    boolean enabled,
    @JsonProperty("expire_at") Long expireAt,
    @JsonProperty("max_cost") Double maxCost,
    @JsonProperty("supported_models") String supportedModels
) {
}
