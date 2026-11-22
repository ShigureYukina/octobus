package io.github.shigureyukina.octobus.security;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.AdminSessionValidator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Minimal auth boundary separating admin JWT and relay API key requirements.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestAuthenticationFilter implements WebFilter {

    public static final String JWT_CLAIMS_ATTRIBUTE = JwtClaims.class.getName();
    public static final String API_KEY_ATTRIBUTE = "octobus.apiKey";

    private static final String ADMIN_API_PREFIX = "/api/v1/";
    private static final String RELAY_API_PREFIX = "/v1/";
    private static final String LOGIN_PATH = "/api/v1/user/login";

    private final JwtService jwtService;
    private final ApiKeyExtractor apiKeyExtractor;
    private final AdminSessionValidator adminSessionValidator;

    public RequestAuthenticationFilter(
        JwtService jwtService,
        ApiKeyExtractor apiKeyExtractor,
        AdminSessionValidator adminSessionValidator
    ) {
        this.jwtService = jwtService;
        this.apiKeyExtractor = apiKeyExtractor;
        this.adminSessionValidator = adminSessionValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // CORS preflight requests must pass through before auth check;
        // CorsWebFilter (which produces the ACAO headers) runs at default order
        // while this filter runs at HIGHEST_PRECEDENCE+10.
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Skip internal endpoints
        if (path.startsWith("/api/internal/")) {
            return chain.filter(exchange);
        }

        if (requiresAdminJwt(path)) {
            JwtClaims claims = verifyJwt(extractBearerToken(exchange.getRequest().getHeaders()));
            adminSessionValidator.validate(claims.subject(), claims.tokenVersion());
            exchange.getAttributes().put(JWT_CLAIMS_ATTRIBUTE, claims);
            return chain.filter(exchange);
        }

        if (requiresRelayApiKey(path)) {
            String apiKey = apiKeyExtractor.extract(exchange.getRequest().getHeaders())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Missing API key"));
            exchange.getAttributes().put(API_KEY_ATTRIBUTE, apiKey);
        }

        return chain.filter(exchange);
    }

    private boolean requiresAdminJwt(String path) {
        return path.startsWith(ADMIN_API_PREFIX) && !LOGIN_PATH.equals(path);
    }

    private boolean requiresRelayApiKey(String path) {
        return path.startsWith(RELAY_API_PREFIX);
    }

    private String extractBearerToken(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }

        String token = authorization.substring(prefix.length()).trim();
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid bearer token");
        }

        return token;
    }

    private JwtClaims verifyJwt(String token) {
        try {
            return jwtService.verify(token);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid bearer token", ex);
        }
    }
}
