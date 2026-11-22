package io.github.shigureyukina.octobus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.config.AuthProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-31T12:00:00Z");

    @Test
    void issuedTokenVerifiesWithConfiguredSecretOnly() {
        JwtService jwtService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        String token = jwtService.issueToken("admin-user");
        JwtClaims claims = jwtService.verify(token);

        assertThat(claims.subject()).isEqualTo("admin-user");
        assertThat(claims.issuedAt()).isEqualTo(FIXED_NOW);
        assertThat(claims.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(3600));
        assertThat(claims.tokenVersion()).isZero();
    }

    @Test
    void issuedTokenCarriesConfiguredTokenVersion() {
        JwtService jwtService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        JwtClaims claims = jwtService.verify(jwtService.issueToken("admin-user", 7, Duration.ofMinutes(5)).token());

        assertThat(claims.tokenVersion()).isEqualTo(7);
        assertThat(claims.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(300));
    }

    @Test
    void verifyFailsWhenTokenWasSignedWithDifferentSecret() {
        JwtService signingService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
        JwtService verifyingService = new JwtService(
            new AuthProperties("other-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        String token = signingService.issueToken("admin-user");

        assertThatThrownBy(() -> verifyingService.verify(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("signature");
    }

    @Test
    void issueTokenRejectsBlankSubject() {
        JwtService jwtService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> jwtService.issueToken(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject");
    }

    @Test
    void verifyRejectsTokenWithTrailingDelimiter() {
        JwtService jwtService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );

        String token = jwtService.issueToken("admin-user") + ".";

        assertThatThrownBy(() -> jwtService.verify(token))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("format");
    }
}
