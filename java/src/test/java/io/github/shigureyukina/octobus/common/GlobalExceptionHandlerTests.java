package io.github.shigureyukina.octobus.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.github\\.shigureyukina\\.octobus\\.controller\\..*"))
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTests.TestController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private io.github.shigureyukina.octobus.security.JwtService jwtService;

    @MockBean
    private io.github.shigureyukina.octobus.security.ApiKeyExtractor apiKeyExtractor;

    @MockBean
    private io.github.shigureyukina.octobus.domain.AdminSessionValidator adminSessionValidator;

    @Test
    void successEnvelopeUsesSharedShape() {
        webTestClient.get()
            .uri("/test/success")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.message").isEqualTo("success")
            .jsonPath("$.data.status").isEqualTo("ok")
            .jsonPath("$.errors").doesNotExist();
    }

    @Test
    void requestBodyValidationMapsToPredictableErrorResponse() {
        webTestClient.post()
            .uri("/test/validation")
            .bodyValue(Map.of("name", "", "count", 0))
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(400)
            .jsonPath("$.message").isEqualTo("Invalid request parameters")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors.length()").isEqualTo(2)
            .jsonPath("$.errors[?(@.field == 'name')].message").isNotEmpty()
            .jsonPath("$.errors[?(@.field == 'count')].message").isNotEmpty();
    }

    @Test
    void parameterValidationMapsToPredictableErrorResponse() {
        webTestClient.get()
            .uri("/test/param?page=0")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(400)
            .jsonPath("$.message").isEqualTo("Invalid request parameters")
            .jsonPath("$.errors.length()").isEqualTo(1)
            .jsonPath("$.errors[0].field").isEqualTo("page");
    }

    @Test
    void requestParameterTypeMismatchMapsToPredictableErrorResponse() {
        webTestClient.get()
            .uri("/test/param?page=oops")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.code").isEqualTo(400)
            .jsonPath("$.message").isEqualTo("Invalid request parameters")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors.length()").isEqualTo(1)
            .jsonPath("$.errors[0].field").isEqualTo("page")
            .jsonPath("$.errors[0].message").isEqualTo("Invalid value");
    }

    @Test
    void apiExceptionUsesDeclaredStatusAndMessage() {
        webTestClient.get()
            .uri("/test/not-found")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo(404)
            .jsonPath("$.message").isEqualTo("Resource not found")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors").doesNotExist();
    }

    @Test
    void unexpectedExceptionUsesGenericInternalErrorMessage() {
        webTestClient.get()
            .uri("/test/unexpected")
            .exchange()
            .expectStatus().isEqualTo(500)
            .expectBody()
            .jsonPath("$.code").isEqualTo(500)
            .jsonPath("$.message").isEqualTo("An unexpected error occurred")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors").doesNotExist();
    }

    @RestController
    @Validated
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("status", "ok"));
        }

        @PostMapping("/validation")
        ApiResponse<Void> validate(@Valid @RequestBody SampleRequest request) {
            return ApiResponse.success(null);
        }

        @GetMapping("/param")
        ApiResponse<Void> validateParameter(@RequestParam @Min(1) int page) {
            return ApiResponse.success(null);
        }

        @GetMapping("/not-found")
        ApiResponse<Void> notFound() {
            throw new ApiException(HttpStatus.NOT_FOUND, "Resource not found");
        }

        @GetMapping("/unexpected")
        ApiResponse<Void> unexpected() {
            throw new IllegalStateException("boom");
        }
    }

    record SampleRequest(
        @NotBlank(message = "must not be blank") String name,
        @Min(value = 1, message = "must be greater than or equal to 1") int count
    ) {
    }
}
