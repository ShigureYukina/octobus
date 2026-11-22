package io.github.shigureyukina.octobus.relay.breaker;

import java.time.Duration;

/**
 * Result of a circuit-breaker permission check.
 */
public record CircuitDecision(
    boolean allowed,
    boolean probe,
    CircuitState state,
    Duration remainingCooldown
) {

    public CircuitDecision {
        state = state == null ? (allowed ? CircuitState.CLOSED : CircuitState.OPEN) : state;
        remainingCooldown = remainingCooldown == null ? Duration.ZERO : remainingCooldown;
    }

    public static CircuitDecision allow() {
        return closed();
    }

    public static CircuitDecision closed() {
        return new CircuitDecision(true, false, CircuitState.CLOSED, Duration.ZERO);
    }

    public static CircuitDecision open(Duration remainingCooldown) {
        return new CircuitDecision(false, false, CircuitState.OPEN, remainingCooldown);
    }

    public static CircuitDecision halfOpenProbe() {
        return new CircuitDecision(true, true, CircuitState.HALF_OPEN, Duration.ZERO);
    }

    public boolean isHalfOpenProbe() {
        return probe;
    }
}
