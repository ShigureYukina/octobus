package io.github.shigureyukina.octobus.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "octobus.web")
public record WebProperties(
    boolean enabled,
    @NotBlank String staticDir,
    @NotBlank String indexFile
) {
}
