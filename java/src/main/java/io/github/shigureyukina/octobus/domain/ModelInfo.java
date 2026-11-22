package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Model pricing information from the model table.
 */
public record ModelInfo(
    @JsonProperty("name") String name,
    @JsonProperty("input") double input,
    @JsonProperty("output") double output,
    @JsonProperty("cache_read") double cacheRead,
    @JsonProperty("cache_write") double cacheWrite
) {
}
