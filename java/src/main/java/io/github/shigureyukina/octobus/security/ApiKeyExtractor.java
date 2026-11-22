package io.github.shigureyukina.octobus.security;

import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Extracts relay API keys from supported request headers.
 */
@Component
public class ApiKeyExtractor {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    public Optional<String> extract(HttpHeaders headers) {
        String apiKeyHeader = headers.getFirst(API_KEY_HEADER);
        if (hasText(apiKeyHeader)) {
            return Optional.of(apiKeyHeader.trim());
        }

        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (!hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return hasText(token) ? Optional.of(token) : Optional.empty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
