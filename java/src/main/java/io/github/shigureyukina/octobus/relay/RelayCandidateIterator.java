package io.github.shigureyukina.octobus.relay;

import io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility wrapper over the T3 balancer iterator.
 *
 * <p>Keeps the original T1/T2 package surface stable while the real
 * implementation now lives under {@code relay.balancer}.
 */
public class RelayCandidateIterator extends io.github.shigureyukina.octobus.relay.balancer.RelayCandidateIterator {

    public RelayCandidateIterator(List<RelayCandidate> candidates) {
        super(candidates);
    }

    public RelayCandidateIterator(
        List<RelayCandidate> candidates,
        StickyStore stickyStore,
        CircuitBreaker circuitBreaker,
        int apiKeyId,
        String requestModel,
        Duration stickyTtl
    ) {
        super(candidates, stickyStore, circuitBreaker, apiKeyId, requestModel, stickyTtl);
    }

    public static RelayCandidateIterator empty() {
        return new RelayCandidateIterator(Collections.emptyList());
    }
}
