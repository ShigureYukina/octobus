package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChangeUsernameRequest(
    @NotBlank @JsonProperty("new_username") String newUsername
) {
}
