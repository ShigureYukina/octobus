package io.github.shigureyukina.octobus.relay.metrics;

/**
 * Model pricing information used for cost calculation.
 *
 * @param input     price per million input tokens
 * @param output    price per million output tokens
 * @param cacheRead price per million cached-read input tokens
 * @param cacheWrite price per million cache-write input tokens
 */
public record ModelPrice(
    double input,
    double output,
    double cacheRead,
    double cacheWrite
) {

    /**
     * Returns a zero-price entry for models without a known price.
     */
    public static ModelPrice zero() {
        return new ModelPrice(0, 0, 0, 0);
    }
}
