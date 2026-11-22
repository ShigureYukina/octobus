package io.github.shigureyukina.octobus.relay.breaker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryCircuitBreakerTests {

    @Test
    void thresholdTripOpensCircuitForExactChannelKeyAndModel() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(3, Duration.ofSeconds(30), Duration.ofMinutes(10), clock);

        breaker.recordFailure(10, 20, "gpt-4o");
        breaker.recordFailure(10, 20, "gpt-4o");

        assertThat(breaker.allow(10, 20, "gpt-4o").allowed()).isTrue();

        breaker.recordFailure(10, 20, "gpt-4o");

        CircuitDecision decision = breaker.allow(10, 20, "gpt-4o");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo(CircuitState.OPEN);
        assertThat(decision.remainingCooldown()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void cooldownExpiryTransitionsOpenToHalfOpenProbe() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(2, Duration.ofSeconds(30), Duration.ofMinutes(10), clock);

        breaker.recordFailure(1, 2, "gpt-4o");
        breaker.recordFailure(1, 2, "gpt-4o");

        clock.advance(Duration.ofSeconds(31));

        CircuitDecision decision = breaker.allow(1, 2, "gpt-4o");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.probe()).isTrue();
        assertThat(decision.state()).isEqualTo(CircuitState.HALF_OPEN);

        CircuitDecision secondDecision = breaker.allow(1, 2, "gpt-4o");
        assertThat(secondDecision.allowed()).isFalse();
        assertThat(secondDecision.state()).isEqualTo(CircuitState.OPEN);
    }

    @Test
    void probeSuccessClosesCircuitAndClearsCooldown() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(2, Duration.ofSeconds(30), Duration.ofMinutes(10), clock);

        breaker.recordFailure(3, 4, "gpt-4o");
        breaker.recordFailure(3, 4, "gpt-4o");
        clock.advance(Duration.ofSeconds(31));

        assertThat(breaker.allow(3, 4, "gpt-4o").probe()).isTrue();

        breaker.recordSuccess(3, 4, "gpt-4o");

        CircuitDecision decision = breaker.allow(3, 4, "gpt-4o");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.probe()).isFalse();
        assertThat(decision.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(decision.remainingCooldown()).isZero();
    }

    @Test
    void probeFailureReopensCircuitWithExponentialBackoff() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(2, Duration.ofSeconds(30), Duration.ofMinutes(10), clock);

        breaker.recordFailure(5, 6, "gpt-4o");
        breaker.recordFailure(5, 6, "gpt-4o");
        clock.advance(Duration.ofSeconds(31));

        assertThat(breaker.allow(5, 6, "gpt-4o").probe()).isTrue();
        breaker.recordFailure(5, 6, "gpt-4o");

        CircuitDecision decision = breaker.allow(5, 6, "gpt-4o");
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.state()).isEqualTo(CircuitState.OPEN);
        assertThat(decision.remainingCooldown()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void breakerStateIsIsolatedAcrossDifferentKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(2, Duration.ofSeconds(30), Duration.ofMinutes(10), clock);

        breaker.recordFailure(7, 8, "gpt-4o");
        breaker.recordFailure(7, 8, "gpt-4o");

        assertThat(breaker.allow(7, 8, "gpt-4o").allowed()).isFalse();
        assertThat(breaker.allow(7, 9, "gpt-4o").allowed()).isTrue();
        assertThat(breaker.allow(7, 8, "gpt-4.1").allowed()).isTrue();
        assertThat(breaker.allow(9, 8, "gpt-4o").allowed()).isTrue();
    }

    @Test
    void cooldownIsCappedByConfiguredMaximum() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T00:00:00Z"));
        InMemoryCircuitBreaker breaker = new InMemoryCircuitBreaker(1, Duration.ofSeconds(30), Duration.ofSeconds(90), clock);

        breaker.recordFailure(11, 12, "gpt-4o");
        clock.advance(Duration.ofSeconds(31));
        assertThat(breaker.allow(11, 12, "gpt-4o").probe()).isTrue();
        breaker.recordFailure(11, 12, "gpt-4o");
        assertThat(breaker.allow(11, 12, "gpt-4o").remainingCooldown()).isEqualTo(Duration.ofSeconds(60));

        clock.advance(Duration.ofSeconds(61));
        assertThat(breaker.allow(11, 12, "gpt-4o").probe()).isTrue();
        breaker.recordFailure(11, 12, "gpt-4o");

        CircuitDecision decision = breaker.allow(11, 12, "gpt-4o");
        assertThat(decision.remainingCooldown()).isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void keyShapeMatchesChannelKeyAndModelContract() {
        assertThat(InMemoryCircuitBreaker.buildKey(1, 2, "gpt-4o")).isEqualTo("1:2:gpt-4o");
    }

    private static final class MutableClock extends Clock {
        private Instant currentInstant;

        private MutableClock(Instant currentInstant) {
            this.currentInstant = currentInstant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }
    }
}
