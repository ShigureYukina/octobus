package io.github.shigureyukina.octobus.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.github.shigureyukina.octobus.domain.AdminSessionValidator;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebFluxTest(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.github\\.shigureyukina\\.octobus\\.controller\\..*"))
@Import({SecurityExceptionHandler.class, SecurityExceptionHandlerTests.TestController.class})
class SecurityExceptionHandlerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private ApiKeyExtractor apiKeyExtractor;

    @MockBean
    private AdminSessionValidator adminSessionValidator;

    @BeforeEach
    void setUp() {
        when(apiKeyExtractor.extract(any())).thenReturn(Optional.empty());
        doNothing().when(adminSessionValidator).validate(anyString(), anyLong());
    }

    @Test
    void relayFilterExceptionUsesSharedErrorEnvelope() {
        webTestClient.get()
            .uri("/v1/test")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing API key")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors").doesNotExist();
    }

    @Test
    void adminFilterExceptionUsesSharedErrorEnvelope() {
        webTestClient.get()
            .uri("/api/v1/secure")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing bearer token")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors").doesNotExist();
    }

    @RestController
    static class TestController {

        @GetMapping("/v1/test")
        Map<String, String> relay() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/v1/secure")
        Map<String, String> admin() {
            return Map.of("status", "ok");
        }

        @GetMapping("/api/v1/user/login")
        Map<String, String> login() {
            return Map.of("status", "ok");
        }
    }
}
