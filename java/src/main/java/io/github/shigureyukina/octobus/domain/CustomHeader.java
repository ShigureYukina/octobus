package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomHeader(
    @JsonProperty("header_key") String headerKey,
    @JsonProperty("header_value") String headerValue
) {
}
