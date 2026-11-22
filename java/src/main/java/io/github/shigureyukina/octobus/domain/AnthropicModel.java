package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Minimal model descriptor returned by the Anthropic /models endpoint.
 */
public record AnthropicModel(
    @JsonProperty("id") String id,
    @JsonProperty("created_at") String createdAt,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("type") String type
) {
}
