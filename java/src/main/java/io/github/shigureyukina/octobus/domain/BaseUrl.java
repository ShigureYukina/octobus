package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BaseUrl(
    String url,
    Integer delay
) {
}
