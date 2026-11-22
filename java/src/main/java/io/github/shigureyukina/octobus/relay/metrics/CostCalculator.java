package io.github.shigureyukina.octobus.relay.metrics;

import io.github.shigureyukina.octobus.relay.metrics.UsageParser.ParsedUsage;

/**
 * Computes relay cost using the three-tier input-cost formula.
 *
 * <p>Matches the Go-side {@code price.GetLLMPrice} cost calculation:
 * <pre>
 *   nonCachedTokens = prompt - cacheRead - cacheWrite
 *   if nonCachedTokens < 0 → fall back to prompt (clamp)
 *   inputCost  = (cacheRead * CacheRead + cacheWrite * CacheWrite + nonCachedTokens * Input) * 1e-6
 *   outputCost = completionTokens * Output * 1e-6
 * </pre>
 *
 * <p>Prices are in USD per million tokens; the {@code 1e-6} factor converts
 * to per-token cost.
 */
public final class CostCalculator {

    private CostCalculator() {
    }

    /**
     * Computes input and output costs for a single relay request.
     *
     * @param usage  parsed token counts (must be non-negative)
     * @param price  model pricing entry
     * @return computed cost breakdown
     */
    public static CostResult calculate(ParsedUsage usage, ModelPrice price) {
        if (price == null) {
            price = ModelPrice.zero();
        }
        long prompt = usage.promptTokens();
        long completion = usage.completionTokens();
        long cacheRead = usage.cacheReadTokens();
        long cacheWrite = usage.cacheWriteTokens();

        // Three-tier input cost: cached-read, cached-write, and fresh tokens.
        // Non-cached input falls back to full prompt when cache details
        // would produce a negative value (defensive clamp).
        long nonCached = prompt - cacheRead - cacheWrite;
        if (nonCached < 0) {
            nonCached = prompt;
        }

        double inputCost = (cacheRead * price.cacheRead()
            + cacheWrite * price.cacheWrite()
            + nonCached * price.input()) * 1e-6;

        double outputCost = completion * price.output() * 1e-6;

        return new CostResult(inputCost, outputCost, inputCost + outputCost);
    }

    /**
     * Computed cost breakdown.
     *
     * @param inputCost  cost of input/prompt tokens
     * @param outputCost cost of output/completion tokens
     * @param totalCost  sum of input and output cost
     */
    public record CostResult(double inputCost, double outputCost, double totalCost) {
    }
}
