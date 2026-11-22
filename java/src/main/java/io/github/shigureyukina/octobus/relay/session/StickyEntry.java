package io.github.shigureyukina.octobus.relay.session;

import java.time.Instant;

/**
 * Sticky-session mapping for a prior successful relay target.
 */
public record StickyEntry(
    int channelId,
    int keyId,
    Instant expiresAt
) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }
}
