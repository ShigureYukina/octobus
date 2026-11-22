package io.github.shigureyukina.octobus.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "octobus.relay")
public record RelayProperties(
    @NotNull @DurationMin(seconds = 1) Duration connectTimeout,
    @NotNull @DurationMin(seconds = 1) Duration readTimeout,
    @Valid @NotNull Retry retry,
    @Valid @NotNull CircuitBreaker circuitBreaker
) {
    public record Retry(
        @PositiveOrZero int maxAttempts,
        @NotNull @DurationMin(millis = 1) Duration backoff
    ) {
    }

    public record CircuitBreaker(
        @Positive int failureThreshold,
        @NotNull @DurationMin(millis = 1) Duration openStateDuration,
        @NotNull @DurationMin(millis = 1) Duration maxCooldown
    ) {
    }
}
