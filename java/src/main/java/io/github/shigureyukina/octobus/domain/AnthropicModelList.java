package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response from the Anthropic /models endpoint.
 */
public record AnthropicModelList(
    @JsonProperty("data") List<AnthropicModel> data,
    @JsonProperty("has_more") Boolean hasMore,
    @JsonProperty("last_id") String lastId,
    @JsonProperty("first_id") String firstId
) {
}
