package io.github.shigureyukina.octobus.domain;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
    @NotBlank String username,
    @NotBlank String password,
    Integer expire
) {
}
