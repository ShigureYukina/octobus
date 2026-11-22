package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.ChannelAttempt;
import io.github.shigureyukina.octobus.relay.RelayCandidate;
import io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker;
import io.github.shigureyukina.octobus.relay.breaker.CircuitDecision;
import io.github.shigureyukina.octobus.relay.session.StickyEntry;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Walks ordered relay candidates with sticky-session priority and
 * circuit-break decision tracking.
 */
public class RelayCandidateIterator {

    private static final String STATUS_SKIPPED = "skipped";
    private static final String STATUS_CIRCUIT_BREAK = "circuit_break";
    private static final CircuitBreaker NOOP_BREAKER = new NoopCircuitBreaker();
    private static final StickyStore NOOP_STICKY_STORE = new NoopStickyStore();

    private final List<RelayCandidate> candidates;
    private final CircuitBreaker circuitBreaker;
    private final List<ChannelAttempt> attempts;
    private final int stickyIndex;

    private int index;
    private int decisionCount;

    public RelayCandidateIterator(List<RelayCandidate> orderedCandidates) {
        this(orderedCandidates, NOOP_STICKY_STORE, NOOP_BREAKER, 0, null, Duration.ZERO);
    }

    public RelayCandidateIterator(
        List<RelayCandidate> orderedCandidates,
        StickyStore stickyStore,
        CircuitBreaker circuitBreaker,
        int apiKeyId,
        String requestModel,
        Duration stickyTtl
    ) {
        this.circuitBreaker = circuitBreaker == null ? NOOP_BREAKER : circuitBreaker;
        this.attempts = new ArrayList<>();
        this.index = -1;
        this.decisionCount = 0;

        StickyStore effectiveStickyStore = stickyStore == null ? NOOP_STICKY_STORE : stickyStore;
        List<RelayCandidate> reordered = new ArrayList<>(orderedCandidates == null ? List.of() : orderedCandidates);
        this.stickyIndex = applyStickyPriority(reordered, effectiveStickyStore, apiKeyId, requestModel, stickyTtl);
        this.candidates = List.copyOf(reordered);
    }

    public boolean hasNext() {
        return index + 1 < candidates.size();
    }

    public RelayCandidate next() {
        while (++index < candidates.size()) {
            RelayCandidate candidate = candidates.get(index);
            CircuitDecision decision = circuitBreaker.allow(
                candidate.channelId(),
                candidate.channelKeyId(),
                candidate.modelName()
            );
            if (decision == null || decision.allowed()) {
                return candidate;
            }
            record(candidate, STATUS_CIRCUIT_BREAK, formatCircuitMessage(decision), 0);
        }
        throw new NoSuchElementException("No more relay candidates available");
    }

    public RelayCandidate current() {
        if (index < 0 || index >= candidates.size()) {
            return null;
        }
        return candidates.get(index);
    }

    public int size() {
        return candidates.size();
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    public int currentIndex() {
        return index;
    }

    public boolean isCurrentSticky() {
        return index >= 0 && index == stickyIndex;
    }

    public void skip(String reason) {
        RelayCandidate candidate = requireCurrent();
        record(candidate, STATUS_SKIPPED, reason, 0);
    }

    public void recordAttempt(Integer channelKeyId, String channelName, String status, String message) {
        recordAttempt(channelKeyId, channelName, status, message, Duration.ZERO);
    }

    public void recordAttempt(
        Integer channelKeyId,
        String channelName,
        String status,
        String message,
        Duration duration
    ) {
        RelayCandidate candidate = requireCurrent();
        RelayCandidate actualCandidate = new RelayCandidate(
            candidate.channelId(),
            channelKeyId == null ? candidate.channelKeyId() : channelKeyId,
            channelName == null ? candidate.channelName() : channelName,
            candidate.modelName()
        );
        int durationMillis = duration == null ? 0 : Math.toIntExact(duration.toMillis());
        record(actualCandidate, status, message, durationMillis);
    }

    public List<ChannelAttempt> attempts() {
        return Collections.unmodifiableList(attempts);
    }

    private RelayCandidate requireCurrent() {
        RelayCandidate candidate = current();
        if (candidate == null) {
            throw new IllegalStateException("No current relay candidate selected");
        }
        return candidate;
    }

    private void record(RelayCandidate candidate, String status, String message, int durationMillis) {
        decisionCount++;
        attempts.add(new ChannelAttempt(
            candidate.channelId(),
            candidate.channelKeyId() > 0 ? candidate.channelKeyId() : null,
            candidate.channelName(),
            candidate.modelName(),
            decisionCount,
            status,
            durationMillis,
            isCurrentSticky() ? Boolean.TRUE : null,
            message
        ));
    }

    private static String formatCircuitMessage(CircuitDecision decision) {
        if (decision.isHalfOpenProbe()) {
            return "circuit breaker half-open probe in progress";
        }
        if (!decision.remainingCooldown().isZero() && !decision.remainingCooldown().isNegative()) {
            return "circuit breaker tripped, remaining cooldown: "
                + decision.remainingCooldown().toSeconds() + "s";
        }
        return "circuit breaker tripped";
    }

    private static int applyStickyPriority(
        List<RelayCandidate> candidates,
        StickyStore stickyStore,
        int apiKeyId,
        String requestModel,
        Duration stickyTtl
    ) {
        if (apiKeyId <= 0 || requestModel == null || requestModel.isBlank() || stickyTtl == null || stickyTtl.isZero() || stickyTtl.isNegative()) {
            return -1;
        }

        Optional<StickyEntry> stickyEntry = stickyStore.getSticky(apiKeyId, requestModel, stickyTtl);
        if (stickyEntry.isEmpty()) {
            return -1;
        }

        int stickyIndex = findStickyIndex(candidates, stickyEntry.get());
        if (stickyIndex <= 0) {
            return stickyIndex;
        }

        RelayCandidate stickyCandidate = candidates.remove(stickyIndex);
        candidates.add(0, stickyCandidate);
        return 0;
    }

    private static int findStickyIndex(List<RelayCandidate> candidates, StickyEntry stickyEntry) {
        int exactMatch = -1;
        int channelMatch = -1;
        for (int i = 0; i < candidates.size(); i++) {
            RelayCandidate candidate = candidates.get(i);
            if (candidate.channelId() != stickyEntry.channelId()) {
                continue;
            }
            if (channelMatch < 0) {
                channelMatch = i;
            }
            if (candidate.channelKeyId() == stickyEntry.keyId()) {
                exactMatch = i;
                break;
            }
        }
        return exactMatch >= 0 ? exactMatch : channelMatch;
    }

    private static final class NoopCircuitBreaker implements CircuitBreaker {

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

    private static final class NoopStickyStore implements StickyStore {

        @Override
        public Optional<StickyEntry> getSticky(int apiKeyId, String requestModel, Duration ttl) {
            return Optional.empty();
        }

        @Override
        public void setSticky(int apiKeyId, String requestModel, int channelId, int keyId) {
        }
    }
}
