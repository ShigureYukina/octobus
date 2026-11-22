package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * A relay log entry matching the Go RelayLog struct shape.
 */
public record RelayLog(
    @JsonProperty("id") long id,
    @JsonProperty("time") long time,
    @JsonProperty("request_model_name") String requestModelName,
    @JsonProperty("request_api_key_name") String requestApiKeyName,
    @JsonProperty("channel") int channelId,
    @JsonProperty("channel_name") String channelName,
    @JsonProperty("actual_model_name") String actualModelName,
    @JsonProperty("input_tokens") int inputTokens,
    @JsonProperty("output_tokens") int outputTokens,
    @JsonProperty("ftut") int ftut,
    @JsonProperty("use_time") int useTime,
    @JsonProperty("cost") double cost,
    @JsonProperty("usage_recorded") boolean usageRecorded,
    @JsonProperty("request_content") String requestContent,
    @JsonProperty("response_content") String responseContent,
    @JsonProperty("error") String error,
    @JsonProperty("attempts") List<ChannelAttempt> attempts,
    @JsonProperty("total_attempts") int totalAttempts,
    @JsonProperty("cache_hit_tokens") int cacheHitTokens
) {
}
