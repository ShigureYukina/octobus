package io.github.shigureyukina.octobus.relay.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InMemoryStickyStoreTests {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    void rememberThenFindReturnsEntryWithinTtl() {
        InMemoryStickyStore store = newStoreAt(NOW);

        store.remember(1, "gpt-4", 42, 7, Duration.ofSeconds(60));

        assertThat(store.find(1, "gpt-4", Duration.ofSeconds(60)))
            .hasValueSatisfying(entry -> {
                assertThat(entry.channelId()).isEqualTo(42);
                assertThat(entry.keyId()).isEqualTo(7);
                assertThat(entry.expiresAt()).isEqualTo(NOW.plusSeconds(60));
            });
    }

    @Test
    void expiredEntryReturnsEmptyAndIsCleanedUpLazily() {
        MutableClock clock = new MutableClock(NOW.minusSeconds(10));
        InMemoryStickyStore store = new InMemoryStickyStore(clock);
        store.remember(2, "gpt-3.5", 99, 5, Duration.ofSeconds(5));
        assertThat(store.size()).isEqualTo(1);

        clock.set(NOW);

        assertThat(store.find(2, "gpt-3.5", Duration.ofSeconds(5))).isEmpty();

        assertThat(store.size()).isZero();
    }

    @Test
    void zeroTtlDisablesStickySessionLookup() {
        InMemoryStickyStore store = newStoreAt(NOW);
        store.remember(3, "gpt-4o", 12, 8, Duration.ofSeconds(30));

        assertThat(store.find(3, "gpt-4o", Duration.ZERO)).isEmpty();
    }

    @Test
    void zeroTtlDoesNotRememberAndRemovesExistingEntry() {
        InMemoryStickyStore store = newStoreAt(NOW);
        store.remember(4, "gpt-4", 1, 2, Duration.ofSeconds(30));

        store.remember(4, "gpt-4", 3, 4, Duration.ZERO);

        assertThat(store.find(4, "gpt-4", Duration.ofSeconds(30))).isEmpty();
        assertThat(store.size()).isZero();
    }

    @Test
    void differentApiKeyOrModelDoesNotMatch() {
        InMemoryStickyStore store = newStoreAt(NOW);
        store.remember(5, "gpt-4", 42, 7, Duration.ofSeconds(60));

        assertThat(store.find(6, "gpt-4", Duration.ofSeconds(60))).isEmpty();
        assertThat(store.find(5, "gpt-3.5", Duration.ofSeconds(60))).isEmpty();
    }

    private static InMemoryStickyStore newStoreAt(Instant instant) {
        return new InMemoryStickyStore(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
