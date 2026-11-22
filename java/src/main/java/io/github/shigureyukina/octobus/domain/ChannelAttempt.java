package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single channel attempt recorded within a relay log entry.
 * Fields annotated with NON_NULL are omitted when null/absent,
 * matching the Go API's omitempty behavior.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChannelAttempt(
    @JsonProperty("channel_id") int channelId,
    @JsonProperty("channel_key_id") Integer channelKeyId,
    @JsonProperty("channel_name") String channelName,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("attempt_num") int attemptNum,
    @JsonProperty("status") String status,
    @JsonProperty("duration") int duration,
    @JsonProperty("sticky") Boolean sticky,
    @JsonProperty("msg") String msg
) {
}
