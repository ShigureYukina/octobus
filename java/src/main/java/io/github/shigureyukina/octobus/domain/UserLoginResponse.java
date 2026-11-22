package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserLoginResponse(
    String token,
    @JsonProperty("expire_at") String expireAt
) {
}
