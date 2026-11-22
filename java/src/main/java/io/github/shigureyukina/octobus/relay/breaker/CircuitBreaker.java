package io.github.shigureyukina.octobus.relay.breaker;

/**
 * Relay circuit-breaker abstraction.
 */
public interface CircuitBreaker {

    CircuitDecision allow(int channelId, int channelKeyId, String modelName);

    void recordSuccess(int channelId, int channelKeyId, String modelName);

    void recordFailure(int channelId, int channelKeyId, String modelName);

    /** Clear all breaker state (primarily for test isolation). */
    default void resetAll() {
    }
}
