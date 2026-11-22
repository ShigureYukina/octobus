package io.github.shigureyukina.octobus.relay.provider;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.relay.RelayAttemptResult;
import io.github.shigureyukina.octobus.relay.spi.OutboundTransformer;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Provider-facing HTTP client abstraction.
 */
public interface ProviderHttpClient {

    Mono<RelayAttemptResult<JsonNode>> postChatCompletion(
        Channel channel,
        ChannelKey channelKey,
        JsonNode payload,
        OutboundTransformer outboundTransformer
    );

    Flux<ServerSentEvent<String>> executeStream(
        Channel channel,
        ChannelKey channelKey,
        JsonNode payload,
        OutboundTransformer outboundTransformer
    );

    /**
     * Send a JSON POST request to a provider endpoint at the given URI path suffix.
     * <p>Default implementation delegates to {@link #postChatCompletion} for backward
     * compatibility. Clients that support generic endpoints override this method.
     */
    default Mono<RelayAttemptResult<JsonNode>> postToEndpoint(
        Channel channel,
        ChannelKey channelKey,
        JsonNode payload,
        OutboundTransformer outboundTransformer,
        String endpointSuffix
    ) {
        return postChatCompletion(channel, channelKey, payload, outboundTransformer);
    }
}
