package io.github.shigureyukina.octobus.relay.spi;

/**
 * Provider usage metrics normalized into one shape for later pricing/logging.
 */
public record UnifiedUsage(
    int inputTokens,
    int outputTokens,
    int cacheReadTokens,
    int cacheWriteTokens,
    int totalTokens
) {
}
