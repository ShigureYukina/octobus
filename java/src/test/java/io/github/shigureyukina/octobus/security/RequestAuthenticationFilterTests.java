package io.github.shigureyukina.octobus.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.config.AuthProperties;
import io.github.shigureyukina.octobus.domain.AdminSessionValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class RequestAuthenticationFilterTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-31T12:00:00Z");

    private RequestAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
        AdminSessionValidator validator = (username, tokenVersion) -> {
        };
        filter = new RequestAuthenticationFilter(jwtService, new ApiKeyExtractor(), validator);
    }

    @Test
    void loginEndpointBypassesAdminJwtBoundary() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/user/login").build()
        );

        filter.filter(exchange, passThroughChain()).block();

        Object jwtClaims = exchange.getAttribute(RequestAuthenticationFilter.JWT_CLAIMS_ATTRIBUTE);
        assertThat(jwtClaims).isNull();
    }

    @Test
    void adminApiRequiresBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/channel/list").build()
        );

        assertThatThrownBy(() -> filter.filter(exchange, passThroughChain()).block())
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getStatus())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminApiAcceptsValidJwtAndExposesClaims() {
        String token = new JwtService(
            new AuthProperties("config-secret"),
            new ObjectMapper(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        ).issueToken("admin-user");

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/channel/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build()
        );

        filter.filter(exchange, passThroughChain()).block();

        JwtClaims claims = exchange.getAttribute(RequestAuthenticationFilter.JWT_CLAIMS_ATTRIBUTE);
        assertThat(claims).isNotNull();
        assertThat(claims.subject()).isEqualTo("admin-user");
    }

    @Test
    void relayApiRequiresApiKeyBoundary() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/v1/chat/completions").build()
        );

        assertThatThrownBy(() -> filter.filter(exchange, passThroughChain()).block())
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).getStatus())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void relayApiStoresExtractedApiKeyForDownstreamUse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/v1/chat/completions")
                .header("X-API-Key", "relay-key-123")
                .build()
        );

        filter.filter(exchange, passThroughChain()).block();

        Object apiKey = exchange.getAttribute(RequestAuthenticationFilter.API_KEY_ATTRIBUTE);
        assertThat(apiKey).isEqualTo("relay-key-123");
    }

    private WebFilterChain passThroughChain() {
        return serverWebExchange -> Mono.empty();
    }
}
