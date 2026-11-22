package io.github.shigureyukina.octobus.relay.spi;

/**
 * Request kind used by outbound transformers to declare compatibility.
 */
public enum UnifiedRequestType {
    CHAT,
    EMBEDDING,
    IMAGE
}
