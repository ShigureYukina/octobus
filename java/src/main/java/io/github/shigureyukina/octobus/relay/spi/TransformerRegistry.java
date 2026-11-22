package io.github.shigureyukina.octobus.relay.spi;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.relay.RelayInboundFormat;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registry/factory for relay inbound and outbound transformers.
 */
public final class TransformerRegistry {

    private final Map<RelayInboundFormat, InboundTransformer> inboundTransformers;
    private final Map<RelayProviderFormat, OutboundTransformer> outboundTransformers;

    public TransformerRegistry(
        Map<RelayInboundFormat, InboundTransformer> inboundTransformers,
        Map<RelayProviderFormat, OutboundTransformer> outboundTransformers
    ) {
        this.inboundTransformers = Map.copyOf(inboundTransformers);
        this.outboundTransformers = Map.copyOf(outboundTransformers);
    }

    public static TransformerRegistry defaultRegistry() {
        return defaultRegistry(new ObjectMapper());
    }

    public static TransformerRegistry defaultRegistry(ObjectMapper objectMapper) {
        Map<RelayInboundFormat, InboundTransformer> inbound = new EnumMap<>(RelayInboundFormat.class);
        inbound.put(RelayInboundFormat.OPENAI_CHAT_COMPLETION, new OpenAIChatInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.OPENAI_RESPONSES, new ResponsesInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.ANTHROPIC_MESSAGES, new AnthropicMessagesInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.OPENAI_EMBEDDINGS, new OpenAIEmbeddingsInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.OPENAI_IMAGE_GENERATION, new OpenAIImageInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.OPENAI_IMAGE_EDIT, new OpenAIImageInboundTransformer(objectMapper));
        inbound.put(RelayInboundFormat.OPENAI_IMAGE_VARIATION, new OpenAIImageInboundTransformer(objectMapper));

        Map<RelayProviderFormat, OutboundTransformer> outbound = new EnumMap<>(RelayProviderFormat.class);
        outbound.put(RelayProviderFormat.OPENAI_CHAT_COMPLETION, new OpenAIChatOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.OPENAI_RESPONSE, new ResponsesOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.OPENAI_EMBEDDING, new OpenAIEmbeddingsOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.OPENAI_IMAGE, new OpenAIImageOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.ANTHROPIC_MESSAGE, new AnthropicMessagesOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.GEMINI_CONTENTS, new GeminiContentsOutboundTransformer(objectMapper));
        outbound.put(RelayProviderFormat.DOUBAO, new UnsupportedOutboundTransformer(RelayProviderFormat.DOUBAO));

        return new TransformerRegistry(inbound, outbound);
    }

    public InboundTransformer resolveInbound(RelayInboundFormat inboundFormat) {
        return inboundTransformers.get(inboundFormat);
    }

    public OutboundTransformer resolveOutbound(RelayProviderFormat providerFormat) {
        return outboundTransformers.get(providerFormat);
    }

    public TransformerPair resolvePair(RelayInboundFormat inboundFormat, RelayProviderFormat providerFormat) {
        return new TransformerPair(resolveInbound(inboundFormat), resolveOutbound(providerFormat));
    }
}
