package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.relay.provider.ProviderHttpClient;
import io.github.shigureyukina.octobus.relay.spi.OutboundTransformer;
import io.github.shigureyukina.octobus.relay.spi.RelayProviderFormat;
import io.github.shigureyukina.octobus.relay.spi.UnifiedRequest;
import reactor.core.publisher.Mono;

/**
 * Single-candidate attempt scaffold.
 *
 * <p>In T1 the only behaviour is to reject with a deterministic
 * "not implemented" failure so that callers can prove the lifecycle
 * is wired end-to-end. The full outbound dispatch (channel HTTP
 * client, header composition, response transformation) is added by
 * T6 ({@code java-relay-openai-chat}).
 */
public class RelayAttempt {

    private final RelayRun run;
    private final RelayCandidate candidate;
    private final Channel channel;
    private final ChannelKey channelKey;
    private final UnifiedRequest request;
    private final RelayRuntimeSupport runtimeSupport;

    public RelayAttempt(RelayRun run, RelayCandidate candidate) {
        this(run, candidate, null, null, null, null);
    }

    public RelayAttempt(
        RelayRun run,
        RelayCandidate candidate,
        Channel channel,
        ChannelKey channelKey,
        UnifiedRequest request,
        RelayRuntimeSupport runtimeSupport
    ) {
        this.run = run;
        this.candidate = candidate;
        this.channel = channel;
        this.channelKey = channelKey;
        this.request = request;
        this.runtimeSupport = runtimeSupport;
    }

    public RelayRun run() {
        return run;
    }

    public RelayCandidate candidate() {
        return candidate;
    }

    public Channel channel() {
        return channel;
    }

    public ChannelKey channelKey() {
        return channelKey;
    }

    public Mono<RelayAttemptResult<JsonNode>> forward() {
        if (runtimeSupport == null || request == null || channel == null || channelKey == null) {
            return Mono.just(RelayAttemptResult.failure(
                "Relay attempt forward() is not yet implemented for "
                    + run.inboundFormat().name()
            ));
        }

        RelayProviderFormat providerFormat = RelayProviderFormat.fromChannelType(channel.type()).orElse(null);
        if (providerFormat == null) {
            return Mono.just(RelayAttemptResult.failure("Channel type is not supported: " + channel.type(), 501));
        }

        OutboundTransformer outboundTransformer = runtimeSupport.transformerRegistry().resolveOutbound(providerFormat);
        if (outboundTransformer == null || outboundTransformer.isGuarded()) {
            return Mono.just(RelayAttemptResult.failure("Outbound provider format is not yet supported for " + providerFormat.name(), 501));
        }

        UnifiedRequest providerRequest = new UnifiedRequest(
            request.requestType(),
            candidate.modelName(),
            request.stream(),
            request.messages(),
            request.tools(),
            request.options(),
            request.rawPayload()
        );

        JsonNode payload = outboundTransformer.serializeRequest(providerRequest);
        payload = applyParamOverride(payload);
        return switch (providerFormat) {
            case OPENAI_CHAT_COMPLETION -> {
                ProviderHttpClient client = runtimeSupport.openAICompatibleProviderClient();
                yield client.postChatCompletion(channel, channelKey, payload, outboundTransformer);
            }
            case ANTHROPIC_MESSAGE -> {
                ProviderHttpClient client = runtimeSupport.anthropicProviderClient();
                yield client.postChatCompletion(channel, channelKey, payload, outboundTransformer);
            }
            case GEMINI_CONTENTS -> {
                ProviderHttpClient client = runtimeSupport.geminiProviderClient();
                yield client.postChatCompletion(channel, channelKey, payload, outboundTransformer);
            }
            case OPENAI_RESPONSE, OPENAI_EMBEDDING, OPENAI_IMAGE -> {
                ProviderHttpClient client = runtimeSupport.openAICompatibleProviderClient();
                String suffix = providerFormat.endpointSuffix();
                yield client.postToEndpoint(channel, channelKey, payload, outboundTransformer, suffix);
            }
            default -> Mono.just(RelayAttemptResult.failure(
                "Outbound provider format is not yet supported for " + providerFormat.name(), 501
            ));
        };
    }

    private JsonNode applyParamOverride(JsonNode payload) {
        if (channel == null) return payload;
        String paramOverride = channel.paramOverride();
        if (paramOverride == null || paramOverride.isBlank()) return payload;
        if (!(payload instanceof ObjectNode payloadNode)) return payload;

        try {
            JsonNode override = runtimeSupport.objectMapper().readTree(paramOverride);
            if (override instanceof ObjectNode overrideNode) {
                overrideNode.fieldNames().forEachRemaining(field ->
                    payloadNode.set(field, overrideNode.get(field)));
            }
        } catch (Exception exception) {
            // Invalid JSON in param_override — skip merge
        }
        return payload;
    }

    public Mono<RelayAttemptResult<Object>> forwardLegacy() {
        return Mono.just(RelayAttemptResult.failure(
            "Relay attempt forward() is not yet implemented for "
                + run.inboundFormat().name()
        ));
    }
}
