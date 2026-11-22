package io.github.shigureyukina.octobus.relay;

/**
 * Single relay candidate selected from a group's items.
 *
 * <p>Mirrors the minimum data the Go version carries on each
 * {@code relayAttempt} before HTTP dispatch. Channel id and key id stay
 * typed as {@code int} for the Java rewrite to match the M2 schema.
 *
 * @param channelId primary key of the channel row
 * @param channelKeyId primary key of the channel_key row (0 when none chosen yet)
 * @param channelName human-readable channel name for logging and metrics
 * @param modelName upstream model that this candidate is configured for
 */
public record RelayCandidate(
    int channelId,
    int channelKeyId,
    String channelName,
    String modelName
) {
}
