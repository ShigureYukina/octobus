package io.github.shigureyukina.octobus.relay.breaker;

import io.github.shigureyukina.octobus.config.RelayProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort in-memory circuit breaker for Milestone 3.
 */
public class InMemoryCircuitBreaker implements CircuitBreaker {

    private final int failureThreshold;
    private final Duration baseCooldown;
    private final Duration maxCooldown;
    private final Clock clock;
    private final ConcurrentHashMap<String, CircuitEntry> entries = new ConcurrentHashMap<>();

    public InMemoryCircuitBreaker(RelayProperties.CircuitBreaker properties) {
        this(
            properties.failureThreshold(),
            properties.openStateDuration(),
            properties.maxCooldown(),
            Clock.systemUTC()
        );
    }

    public InMemoryCircuitBreaker(
        int failureThreshold,
        Duration baseCooldown,
        Duration maxCooldown
    ) {
        this(failureThreshold, baseCooldown, maxCooldown, Clock.systemUTC());
    }

    InMemoryCircuitBreaker(
        int failureThreshold,
        Duration baseCooldown,
        Duration maxCooldown,
        Clock clock
    ) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (baseCooldown == null || baseCooldown.isZero() || baseCooldown.isNegative()) {
            throw new IllegalArgumentException("baseCooldown must be positive");
        }
        if (maxCooldown == null || maxCooldown.isZero() || maxCooldown.isNegative()) {
            throw new IllegalArgumentException("maxCooldown must be positive");
        }
        if (maxCooldown.compareTo(baseCooldown) < 0) {
            throw new IllegalArgumentException("maxCooldown must be >= baseCooldown");
        }
        this.failureThreshold = failureThreshold;
        this.baseCooldown = baseCooldown;
        this.maxCooldown = maxCooldown;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CircuitDecision allow(int channelId, int channelKeyId, String modelName) {
        String key = buildKey(channelId, channelKeyId, modelName);
        CircuitEntry entry = entries.get(key);
        if (entry == null) {
            return CircuitDecision.closed();
        }

        Instant now = now();
        synchronized (entry) {
            if (entry.state == CircuitState.CLOSED) {
                return CircuitDecision.closed();
            }

            if (entry.state == CircuitState.HALF_OPEN) {
                return CircuitDecision.open(Duration.ZERO);
            }

            Duration cooldown = cooldownForTripCount(entry.tripCount);
            Duration elapsed = Duration.between(entry.lastFailureAt, now);
            if (elapsed.compareTo(cooldown) >= 0) {
                entry.state = CircuitState.HALF_OPEN;
                return CircuitDecision.halfOpenProbe();
            }

            return CircuitDecision.open(cooldown.minus(elapsed));
        }
    }

    @Override
    public void recordSuccess(int channelId, int channelKeyId, String modelName) {
        String key = buildKey(channelId, channelKeyId, modelName);
        CircuitEntry entry = entries.get(key);
        if (entry == null) {
            return;
        }

        synchronized (entry) {
            entry.state = CircuitState.CLOSED;
            entry.consecutiveFailures = 0;
            entry.tripCount = 0;
            entry.lastFailureAt = null;
        }
    }

    @Override
    public void recordFailure(int channelId, int channelKeyId, String modelName) {
        String key = buildKey(channelId, channelKeyId, modelName);
        CircuitEntry entry = entries.computeIfAbsent(key, ignored -> new CircuitEntry());
        Instant now = now();

        synchronized (entry) {
            entry.lastFailureAt = now;
            if (entry.state == CircuitState.HALF_OPEN) {
                entry.state = CircuitState.OPEN;
                entry.tripCount++;
                entry.consecutiveFailures = 0;
                return;
            }

            if (entry.state == CircuitState.OPEN) {
                return;
            }

            entry.consecutiveFailures++;
            if (entry.consecutiveFailures >= failureThreshold) {
                entry.state = CircuitState.OPEN;
                entry.tripCount++;
            }
        }
    }

    static String buildKey(int channelId, int channelKeyId, String modelName) {
        return channelId + ":" + channelKeyId + ":" + Objects.requireNonNull(modelName, "modelName");
    }

    Duration cooldownForTripCount(int tripCount) {
        if (tripCount <= 0) {
            return baseCooldown;
        }

        Duration cooldown = baseCooldown;
        for (int i = 1; i < tripCount; i++) {
            if (cooldown.compareTo(maxCooldown) >= 0) {
                return maxCooldown;
            }

            Duration doubled = cooldown.multipliedBy(2);
            if (doubled.compareTo(maxCooldown) >= 0 || doubled.isNegative()) {
                return maxCooldown;
            }
            cooldown = doubled;
        }
        return cooldown.compareTo(maxCooldown) > 0 ? maxCooldown : cooldown;
    }

    @Override
    public void resetAll() {
        entries.clear();
    }

    private Instant now() {
        return clock.instant();
    }

    private static final class CircuitEntry {
        private CircuitState state = CircuitState.CLOSED;
        private int consecutiveFailures;
        private Instant lastFailureAt;
        private int tripCount;
    }
}
