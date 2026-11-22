package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Serializes the unified request for one provider-facing protocol and parses the response.
 */
public interface OutboundTransformer {

    RelayProviderFormat providerFormat();

    boolean supports(UnifiedRequestType requestType);

    JsonNode serializeRequest(UnifiedRequest request);

    UnifiedResponse parseResponse(String rawBody);

    default boolean isGuarded() {
        return false;
    }
}
