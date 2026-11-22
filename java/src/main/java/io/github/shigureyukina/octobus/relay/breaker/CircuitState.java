package io.github.shigureyukina.octobus.relay.breaker;

/**
 * In-process circuit breaker states for a single channel/key/model tuple.
 */
public enum CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
