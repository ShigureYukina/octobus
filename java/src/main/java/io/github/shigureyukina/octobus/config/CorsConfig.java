package io.github.shigureyukina.octobus.config;

import io.github.shigureyukina.octobus.domain.SettingService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS configuration sourced from the {@code cors_allow_origins} setting.
 *
 * <p>Supports comma-separated origin lists and the {@code *} wildcard.
 * If the setting is empty, all origins are allowed.
 */
@Configuration
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
        "Content-Type", "Authorization", "X-API-Key", "x-api-key"
    );

    @Bean
    public CorsWebFilter corsWebFilter(SettingService settingService) {
        String allowedOrigins = settingService.list().stream()
            .filter(s -> SettingService.CORS_ALLOW_ORIGINS.equals(s.key()))
            .map(io.github.shigureyukina.octobus.domain.Setting::value)
            .findFirst()
            .orElse("");

        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
        if (allowedOrigins.isBlank() || allowedOrigins.equals("*")) {
            config.addAllowedOriginPattern("*");
        } else {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    config.addAllowedOrigin(trimmed);
                }
            }
        }
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
