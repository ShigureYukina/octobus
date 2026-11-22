package io.github.shigureyukina.octobus.relay.session;

import java.time.Duration;
import java.util.Optional;

/**
 * Sticky-session lookup/write abstraction.
 */
public interface StickyStore {

    Optional<StickyEntry> getSticky(int apiKeyId, String requestModel, Duration ttl);

    void setSticky(int apiKeyId, String requestModel, int channelId, int keyId);

    default Optional<StickyEntry> find(int apiKeyId, String requestModel, Duration ttl) {
        return getSticky(apiKeyId, requestModel, ttl);
    }

    default void remember(int apiKeyId, String requestModel, int channelId, int keyId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            remove(apiKeyId, requestModel);
            return;
        }
        setSticky(apiKeyId, requestModel, channelId, keyId);
    }

    default void remove(int apiKeyId, String requestModel) {
    }

    /** Clear all sticky state (primarily for test isolation). */
    default void clear() {
    }
}
