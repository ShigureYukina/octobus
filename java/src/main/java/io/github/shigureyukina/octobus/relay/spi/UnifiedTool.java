package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tool metadata carried through the unified relay request model.
 */
public record UnifiedTool(
    String name,
    String description,
    JsonNode schema
) {
}
