package io.github.shigureyukina.octobus.relay.spi;

/**
 * Resolved inbound/outbound transformer pair for a relay path.
 */
public record TransformerPair(
    InboundTransformer inbound,
    OutboundTransformer outbound
) {
}
