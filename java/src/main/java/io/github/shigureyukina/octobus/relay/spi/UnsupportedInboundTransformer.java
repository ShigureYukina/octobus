package io.github.shigureyukina.octobus.relay.spi;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import org.springframework.http.HttpStatus;

/**
 * Guarded inbound transformer used for formats deferred to later milestones.
 */
final class UnsupportedInboundTransformer implements InboundTransformer {

    private final RelayInboundFormat inboundFormat;
    private final String message;

    UnsupportedInboundTransformer(RelayInboundFormat inboundFormat) {
        this.inboundFormat = inboundFormat;
        this.message = "Inbound format is not yet supported for " + inboundFormat.name();
    }

    @Override
    public RelayInboundFormat inboundFormat() {
        return inboundFormat;
    }

    @Override
    public UnifiedRequest transform(String rawBody) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, message);
    }

    @Override
    public boolean isGuarded() {
        return true;
    }
}
