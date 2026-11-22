package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Message entry in the unified relay request model.
 */
public record UnifiedMessage(
    String role,
    JsonNode content
) {
}
