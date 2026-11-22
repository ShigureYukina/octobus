package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Canonical relay request used between inbound parsing and outbound emission.
 */
public record UnifiedRequest(
    UnifiedRequestType requestType,
    String model,
    boolean stream,
    List<UnifiedMessage> messages,
    List<UnifiedTool> tools,
    Map<String, JsonNode> options,
    JsonNode rawPayload
) {
}
