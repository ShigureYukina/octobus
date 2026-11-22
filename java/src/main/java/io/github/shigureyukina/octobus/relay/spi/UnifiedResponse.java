package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Canonical relay response shape used before it is mapped back to the client API.
 */
public record UnifiedResponse(
    String model,
    UnifiedUsage usage,
    JsonNode primaryContent,
    JsonNode rawPayload
) {
}
