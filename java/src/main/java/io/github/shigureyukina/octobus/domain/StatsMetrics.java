package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shared metrics fields used by stats_total, stats_daily, and stats_hourly records.
 */
public record StatsMetrics(
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
     * Returns a zero-valued metrics instance.
     */
    public static StatsMetrics zero() {
        return new StatsMetrics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
