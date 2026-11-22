package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregated total stats matching the Go StatsTotal struct.
 */
public record StatsTotal(
    @JsonProperty("id") int id,
    @JsonProperty("input_token") long inputToken,
    @JsonProperty("output_token") long outputToken,
    @JsonProperty("input_cost") double inputCost,
    @JsonProperty("output_cost") double outputCost,
    @JsonProperty("wait_time") long waitTime,
    @JsonProperty("request_success") long requestSuccess,
    @JsonProperty("request_failed") long requestFailed,
    @JsonProperty("cache_hit_token") long cacheHitToken
) {

    /**
     * Creates a StatsTotal from an id and metrics.
     */
    public static StatsTotal of(int id, StatsMetrics metrics) {
        return new StatsTotal(
            id,
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
