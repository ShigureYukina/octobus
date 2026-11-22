package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.shigureyukina.octobus.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Guarded outbound transformer used until a provider path is implemented.
 */
final class UnsupportedOutboundTransformer implements OutboundTransformer {

    private final RelayProviderFormat providerFormat;
    private final String message;

    UnsupportedOutboundTransformer(RelayProviderFormat providerFormat) {
        this.providerFormat = providerFormat;
        this.message = "Outbound provider format is not yet supported for " + providerFormat.name();
    }

    @Override
    public RelayProviderFormat providerFormat() {
        return providerFormat;
    }

    @Override
    public boolean supports(UnifiedRequestType requestType) {
        return false;
    }

    @Override
    public JsonNode serializeRequest(UnifiedRequest request) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, message);
    }

    @Override
    public UnifiedResponse parseResponse(String rawBody) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, message);
    }

    @Override
    public boolean isGuarded() {
        return true;
    }
}
