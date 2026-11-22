package io.github.shigureyukina.octobus.relay.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-process sticky-session store for Milestone 3 relay candidate reuse.
 */
public class InMemoryStickyStore implements StickyStore {

    private final ConcurrentMap<StickyKey, StickyEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryStickyStore() {
        this(Clock.systemUTC());
    }

    InMemoryStickyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<StickyEntry> getSticky(int apiKeyId, String requestModel, Duration ttl) {
        if (isDisabled(ttl)) {
            return Optional.empty();
        }

        StickyKey key = new StickyKey(apiKeyId, normalizeModel(requestModel));
        StickyEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        if (isExpired(entry, Instant.now(clock))) {
            entries.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    @Override
    public void setSticky(int apiKeyId, String requestModel, int channelId, int keyId) {
        Instant expiresAt = Instant.now(clock).plus(Duration.ofHours(24));
        entries.put(
            new StickyKey(apiKeyId, normalizeModel(requestModel)),
            new StickyEntry(channelId, keyId, expiresAt)
        );
    }

    public void remember(
        int apiKeyId,
        String requestModel,
        int channelId,
        int keyId,
        Duration sessionKeepTime
    ) {
        StickyKey key = new StickyKey(apiKeyId, normalizeModel(requestModel));
        if (isDisabled(sessionKeepTime)) {
            entries.remove(key);
            return;
        }

        Instant expiresAt = Instant.now(clock).plus(sessionKeepTime);
        entries.put(key, new StickyEntry(channelId, keyId, expiresAt));
    }

    public void remove(int apiKeyId, String requestModel) {
        entries.remove(new StickyKey(apiKeyId, normalizeModel(requestModel)));
    }

    @Override
    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private static boolean isDisabled(Duration sessionKeepTime) {
        return sessionKeepTime == null || sessionKeepTime.isZero() || sessionKeepTime.isNegative();
    }

    private static boolean isExpired(StickyEntry entry, Instant now) {
        return entry.expiresAt() != null && entry.expiresAt().isBefore(now);
    }

    private static String normalizeModel(String requestModel) {
        return requestModel == null ? "" : requestModel;
    }

    private record StickyKey(int apiKeyId, String requestModel) {
    }
}
