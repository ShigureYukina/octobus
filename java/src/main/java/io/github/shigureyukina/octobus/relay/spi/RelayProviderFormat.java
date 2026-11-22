package io.github.shigureyukina.octobus.relay.spi;

import java.util.Locale;
import java.util.Optional;

/**
 * Provider-facing protocol targets available to the relay runtime.
 */
public enum RelayProviderFormat {
    OPENAI_CHAT_COMPLETION,
    OPENAI_RESPONSE,
    OPENAI_EMBEDDING,
    OPENAI_IMAGE,
    ANTHROPIC_MESSAGE,
    GEMINI_CONTENTS,
    DOUBAO;

    /**
     * Returns the URL path suffix for this provider's API endpoint.
     * Used to build the correct upstream URI when forwarding requests.
     */
    public String endpointSuffix() {
        return switch (this) {
            case OPENAI_CHAT_COMPLETION -> "/chat/completions";
            case OPENAI_RESPONSE -> "/responses";
            case OPENAI_EMBEDDING -> "/embeddings";
            case OPENAI_IMAGE -> "/images/generations";
            case ANTHROPIC_MESSAGE -> "/messages";
            case GEMINI_CONTENTS -> throw new UnsupportedOperationException("Gemini endpoint not implemented");
            case DOUBAO -> throw new UnsupportedOperationException("Doubao endpoint not implemented");
        };
    }

    public static Optional<RelayProviderFormat> fromChannelType(String channelType) {
        if (channelType == null || channelType.isBlank()) {
            return Optional.empty();
        }
        String normalized = channelType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "openai/chat_completions" -> Optional.of(OPENAI_CHAT_COMPLETION);
            case "openai/responses" -> Optional.of(OPENAI_RESPONSE);
            case "openai/embeddings" -> Optional.of(OPENAI_EMBEDDING);
            case "openai/image_generation", "openai/image_edit", "openai/image_variation" -> Optional.of(OPENAI_IMAGE);
            case "anthropic/messages" -> Optional.of(ANTHROPIC_MESSAGE);
            case "gemini/contents" -> Optional.of(GEMINI_CONTENTS);
            case "doubao" -> Optional.of(DOUBAO);
            default -> Optional.empty();
        };
    }
}
