package io.github.shigureyukina.octobus.relay.spi;

import io.github.shigureyukina.octobus.relay.RelayInboundFormat;

/**
 * Normalizes one client-facing API payload into the unified request model.
 */
public interface InboundTransformer {

    RelayInboundFormat inboundFormat();

    UnifiedRequest transform(String rawBody);

    default boolean isGuarded() {
        return false;
    }
}
