package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Hourly-aggregated stats matching the Go StatsHourly struct.
 */
public record StatsHourly(
    @JsonProperty("hour") int hour,
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
     * Creates a StatsHourly from hour, date, and metrics.
     */
    public static StatsHourly of(int hour, String date, StatsMetrics metrics) {
        return new StatsHourly(
            hour,
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
