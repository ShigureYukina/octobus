package io.github.shigureyukina.octobus.security;

import java.time.Instant;

/**
 * Verified JWT claims made available to downstream handlers.
 */
public record JwtClaims(
    String subject,
    Instant issuedAt,
    Instant expiresAt,
    long tokenVersion
) {
}
