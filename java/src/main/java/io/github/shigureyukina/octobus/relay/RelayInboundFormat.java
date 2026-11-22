package io.github.shigureyukina.octobus.relay;

import java.util.EnumSet;
import java.util.Set;

/**
 * Inbound API formats accepted by the relay.
 *
 * <p>Each value maps to a single {@code POST} route under {@code /v1/*}.
 * Stream capability is tracked here so that the handler can short-circuit
 * streaming requests with a 501 response before any adapter is wired.
 */
public enum RelayInboundFormat {
    OPENAI_CHAT_COMPLETION("/v1/chat/completions", true),
    OPENAI_RESPONSES("/v1/responses", true),
    ANTHROPIC_MESSAGES("/v1/messages", true),
    OPENAI_EMBEDDINGS("/v1/embeddings", false),
    OPENAI_IMAGE_GENERATION("/v1/images/generations", true),
    OPENAI_IMAGE_EDIT("/v1/images/edits", true),
    OPENAI_IMAGE_VARIATION("/v1/images/variations", true);

    private static final Set<RelayInboundFormat> STREAM_CAPABLE = EnumSet.of(
        OPENAI_CHAT_COMPLETION,
        OPENAI_RESPONSES,
        ANTHROPIC_MESSAGES,
        OPENAI_IMAGE_GENERATION,
        OPENAI_IMAGE_EDIT,
        OPENAI_IMAGE_VARIATION
    );

    private final String path;
    private final boolean streamCapable;

    RelayInboundFormat(String path, boolean streamCapable) {
        this.path = path;
        this.streamCapable = streamCapable;
    }

    public String path() {
        return path;
    }

    public boolean isStreamCapable() {
        return streamCapable;
    }

    public static boolean supportsStreaming(RelayInboundFormat format) {
        return STREAM_CAPABLE.contains(format);
    }
}
