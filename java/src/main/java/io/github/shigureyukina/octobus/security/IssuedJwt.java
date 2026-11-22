package io.github.shigureyukina.octobus.security;

import java.time.Instant;

public record IssuedJwt(
    String token,
    Instant expiresAt
) {
}
