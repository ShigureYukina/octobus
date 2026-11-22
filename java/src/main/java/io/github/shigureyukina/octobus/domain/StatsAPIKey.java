package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Per-API-key aggregated stats matching the Go StatsAPIKey struct.
 */
public record StatsAPIKey(
    @JsonProperty("api_key_id") int apiKeyId,
    @JsonProperty("input_token") long inputToken,
    @JsonProperty("output_token") long outputToken,
    @JsonProperty("input_cost") double inputCost,
    @JsonProperty("output_cost") double outputCost,
    @JsonProperty("wait_time") long waitTime,
    @JsonProperty("request_success") long requestSuccess,
    @JsonProperty("request_failed") long requestFailed,
    @JsonProperty("cache_hit_token") long cacheHitToken
) {

    public static StatsAPIKey of(int apiKeyId, StatsMetrics metrics) {
        return new StatsAPIKey(
            apiKeyId,
            metrics.inputToken(),
            metrics.outputToken(),
            metrics.inputCost(),
            metrics.outputCost(),
            metrics.waitTime(),
            metrics.requestSuccess(),
            metrics.requestFailed(),
            metrics.cacheHitToken()
        );
    }
}
