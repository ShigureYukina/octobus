package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAIModel(
    @JsonProperty("id") String id,
    @JsonProperty("object") String object,
    @JsonProperty("created") long created,
    @JsonProperty("owned_by") String ownedBy
) {
}
