package io.github.shigureyukina.octobus.relay.balancer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shigureyukina.octobus.domain.ChannelAttempt;
import io.github.shigureyukina.octobus.relay.RelayCandidate;
import io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker;
import io.github.shigureyukina.octobus.relay.breaker.CircuitDecision;
import io.github.shigureyukina.octobus.relay.session.StickyEntry;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RelayCandidateIteratorTests {

    @Test
    void iteratorUsesOriginalOrderingWhenNoStickyOrBreakerApplies() {
        RelayCandidateIterator iterator = new RelayCandidateIterator(candidates());

        assertThat(iterator.next().channelId()).isEqualTo(1);
        assertThat(iterator.current().channelId()).isEqualTo(1);
        assertThat(iterator.next().channelId()).isEqualTo(2);
        assertThat(iterator.next().channelId()).isEqualTo(3);
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void iteratorPrefersStickyCandidateWhenEnabled() {
        RelayCandidateIterator iterator = new RelayCandidateIterator(
            candidates(),
            new FakeStickyStore(new StickyEntry(2, 22, Instant.now().plusSeconds(30))),
            new AllowAllBreaker(),
            99,
            "gpt-4",
            Duration.ofSeconds(60)
        );

        RelayCandidate current = iterator.next();

        assertThat(current.channelId()).isEqualTo(2);
        assertThat(iterator.isCurrentSticky()).isTrue();
    }

    @Test
    void iteratorIgnoresStickyWhenTtlIsZero() {
        RelayCandidateIterator iterator = new RelayCandidateIterator(
            candidates(),
            new FakeStickyStore(new StickyEntry(2, 22, Instant.now().plusSeconds(30))),
            new AllowAllBreaker(),
            99,
            "gpt-4",
            Duration.ZERO
        );

        assertThat(iterator.next().channelId()).isEqualTo(1);
        assertThat(iterator.isCurrentSticky()).isFalse();
    }

    @Test
    void iteratorRecordsCircuitBreakerSkipsAndMovesToNextAllowedCandidate() {
        RelayCandidateIterator iterator = new RelayCandidateIterator(
            candidates(),
            null,
            new FakeCircuitBreaker(List.of(
                CircuitDecision.open(Duration.ofSeconds(30)),
                CircuitDecision.allow()
            )),
            0,
            null,
            Duration.ZERO
        );

        RelayCandidate candidate = iterator.next();

        assertThat(candidate.channelId()).isEqualTo(2);
        assertThat(iterator.attempts())
            .singleElement()
            .extracting(ChannelAttempt::status, ChannelAttempt::msg)
            .containsExactly("circuit_break", "circuit breaker tripped, remaining cooldown: 30s");
    }

    @Test
    void iteratorTracksExplicitSkipAndAttemptChain() {
        RelayCandidateIterator iterator = new RelayCandidateIterator(candidates());

        iterator.next();
        iterator.skip("no available key");
        iterator.next();
        iterator.recordAttempt(22, "channel-b", "success", "forwarded", Duration.ofMillis(45));

        assertThat(iterator.attempts())
            .extracting(ChannelAttempt::attemptNum, ChannelAttempt::status, ChannelAttempt::channelId, ChannelAttempt::duration)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "skipped", 1, 0),
                org.assertj.core.groups.Tuple.tuple(2, "success", 2, 45)
            );
    }

    private static List<RelayCandidate> candidates() {
        return List.of(
            new RelayCandidate(1, 11, "channel-a", "gpt-4"),
            new RelayCandidate(2, 22, "channel-b", "gpt-4"),
            new RelayCandidate(3, 33, "channel-c", "gpt-4")
        );
    }

    private static final class FakeStickyStore implements StickyStore {

        private final StickyEntry entry;

        private FakeStickyStore(StickyEntry entry) {
            this.entry = entry;
        }

        @Override
        public Optional<StickyEntry> getSticky(int apiKeyId, String requestModel, Duration ttl) {
            return Optional.ofNullable(entry);
        }

        @Override
        public void setSticky(int apiKeyId, String requestModel, int channelId, int keyId) {
        }
    }

    private static final class AllowAllBreaker implements CircuitBreaker {

        @Override
        public CircuitDecision allow(int channelId, int channelKeyId, String modelName) {
            return CircuitDecision.allow();
        }

        @Override
        public void recordSuccess(int channelId, int channelKeyId, String modelName) {
        }

        @Override
        public void recordFailure(int channelId, int channelKeyId, String modelName) {
        }
    }

    private static final class FakeCircuitBreaker implements CircuitBreaker {

        private final List<CircuitDecision> decisions;
        private int index;

        private FakeCircuitBreaker(List<CircuitDecision> decisions) {
            this.decisions = decisions;
            this.index = 0;
        }

        @Override
        public CircuitDecision allow(int channelId, int channelKeyId, String modelName) {
            if (index >= decisions.size()) {
                return CircuitDecision.allow();
            }
            return decisions.get(index++);
        }

        @Override
        public void recordSuccess(int channelId, int channelKeyId, String modelName) {
        }

        @Override
        public void recordFailure(int channelId, int channelKeyId, String modelName) {
        }
    }
}
