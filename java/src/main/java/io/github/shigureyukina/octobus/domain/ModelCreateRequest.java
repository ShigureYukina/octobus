package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ModelCreateRequest(
    String name,
    double input,
    double output,
    @JsonProperty("cache_read") double cacheRead,
    @JsonProperty("cache_write") double cacheWrite
) {
}
