package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Daily-aggregated stats matching the Go StatsDaily struct.
 */
public record StatsDaily(
    @JsonProperty("date") String date,
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
     * Creates a StatsDaily from a date string and metrics.
     */
    public static StatsDaily of(String date, StatsMetrics metrics) {
        return new StatsDaily(
            date,
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
