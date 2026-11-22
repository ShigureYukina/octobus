package io.github.shigureyukina.octobus.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "octobus.auth")
public record AuthProperties(
    @NotBlank String jwtSecret
) {
}
