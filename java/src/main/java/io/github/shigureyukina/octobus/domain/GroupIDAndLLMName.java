package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight pair for batch group-item operations.
 *
 * @param channelId the channel's primary key
 * @param modelName the model name to associate
 */
public record GroupIDAndLLMName(
    @JsonProperty("channel_id") int channelId,
    @JsonProperty("model_name") String modelName
) {
}
