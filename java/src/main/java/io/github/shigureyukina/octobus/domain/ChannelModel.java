package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model-to-channel association returned by the /api/v1/model/channel endpoint.
 *
 * @param name        the model name
 * @param enabled     whether the parent channel is enabled
 * @param channelId   the parent channel PK
 * @param channelName the parent channel name
 */
public record ChannelModel(
    @JsonProperty("name") String name,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("channel_id") int channelId,
    @JsonProperty("channel_name") String channelName
) {
}
