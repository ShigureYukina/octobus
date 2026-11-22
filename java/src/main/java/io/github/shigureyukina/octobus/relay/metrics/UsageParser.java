package io.github.shigureyukina.octobus.relay.metrics;

import io.github.shigureyukina.octobus.relay.spi.UnifiedUsage;

/**
 * Parses token usage from a relay response.
 *
 * <p>In Milestone 3 the outbound transformer already produces a
 * {@link UnifiedUsage} record; this class validates and normalises it
 * before the cost calculator consumes it.
 */
public final class UsageParser {

    private UsageParser() {
    }

    /**
     * Extracts token counts from a unified usage object.
     *
     * @param usage the usage record from the outbound transformer; may be null
     * @return a ParsedUsage with non-negative token counts
     */
    public static ParsedUsage parse(UnifiedUsage usage) {
        if (usage == null) {
            return new ParsedUsage(0, 0, 0, 0);
        }
        return new ParsedUsage(
            Math.max(0, usage.inputTokens()),
            Math.max(0, usage.outputTokens()),
            Math.max(0, usage.cacheReadTokens()),
            Math.max(0, usage.cacheWriteTokens())
        );
    }

    /**
     * Sanitized token counts ready for cost calculation.
     *
     * @param promptTokens     total input/prompt tokens
     * @param completionTokens total output/completion tokens
     * @param cacheReadTokens  cached prompt tokens read
     * @param cacheWriteTokens prompt tokens written to cache
     */
    public record ParsedUsage(
        long promptTokens,
        long completionTokens,
        long cacheReadTokens,
        long cacheWriteTokens
    ) {
    }
}
