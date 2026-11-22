package io.github.shigureyukina.octobus.relay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.common.GlobalExceptionHandler;
import io.github.shigureyukina.octobus.domain.AdminSessionValidator;
import io.github.shigureyukina.octobus.security.ApiKeyExtractor;
import io.github.shigureyukina.octobus.security.JwtService;
import io.github.shigureyukina.octobus.security.RequestAuthenticationFilter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "io\\.github\\.shigureyukina\\.octobus\\.controller\\..*"))
@Import({RelayRouter.class, GlobalExceptionHandler.class, RequestAuthenticationFilter.class})
class RelayHandlerTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private ApiKeyExtractor apiKeyExtractor;

    @MockBean
    private AdminSessionValidator adminSessionValidator;

    @BeforeEach
    void setUp() {
        when(apiKeyExtractor.extract(any())).thenReturn(Optional.of("test-key"));
    }

    @Test
    void nonStreamRequestReturnsAdapterNotWiredEnvelope() {
        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<?> response = parse(body);
        assertThat(response.code()).isEqualTo(501);
        assertThat(response.message()).isNotBlank();
        assertThat(response.data()).isNull();
    }

    @Test
    void streamRequestReturnsNotImplementedWhenRuntimeIsNotWired() {
        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\",\"stream\":true,\"messages\":[]}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<?> response = parse(body);
        assertThat(response.code()).isEqualTo(501);
        assertThat(response.message()).containsIgnoringCase("streaming");
    }

    @Test
    void relayHandlerDelegatesToStubbedRelayRun() {
        RelayHandler handler = new RelayHandler(
            RelayInboundFormat.OPENAI_CHAT_COMPLETION,
            (body, fmt) -> new RelayRun(body, fmt, RelayCandidateIterator.empty()) {
                @Override
                public Mono<ResponseEntity<?>> execute() {
                    return Mono.just(ResponseEntity.ok(ApiResponse.success("stub-result")));
                }
            },
            objectMapper
        );

        RouterFunction<ServerResponse> routes = RouterFunctions.route()
            .POST("/v1/test", handler::handle)
            .build();

        String body = WebTestClient.bindToRouterFunction(routes).build()
            .post()
            .uri("/v1/test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<?> response = parse(body);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("stub-result");
    }

    @Test
    void relayHandlerCanReturnStreamingServerResponseFromStubbedRun() {
        RelayHandler handler = new RelayHandler(
            RelayInboundFormat.OPENAI_CHAT_COMPLETION,
            (request, body, fmt) -> new RelayRun(body, fmt, RelayCandidateIterator.empty()) {
                @Override
                public Flux<ServerSentEvent<String>> executeStream() {
                    return Flux.just(
                        ServerSentEvent.<String>builder().data("first").build(),
                        ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                }
            },
            objectMapper
        );

        RouterFunction<ServerResponse> routes = RouterFunctions.route()
            .POST("/v1/test-stream", handler::handle)
            .build();

        String body = WebTestClient.bindToRouterFunction(routes).build()
            .post()
            .uri("/v1/test-stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\",\"stream\":true}")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class)
            .getResponseBody()
            .collectList()
            .block()
            .stream()
            .reduce("", String::concat);

        assertThat(body).contains("first");
        assertThat(body).contains("[DONE]");
    }

    @Test
    void v1ChatCompletionsWithoutStreamReturns501() {
        webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501)
            .jsonPath("$.message").isNotEmpty()
            .jsonPath("$.data").doesNotExist();
    }

    @Test
    void v1ResponsesRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/responses")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501)
            .jsonPath("$.message").isNotEmpty();
    }

    @Test
    void v1MessagesRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/messages")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"claude-3-opus\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void v1EmbeddingsRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/embeddings")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"text-embedding-ada-002\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void v1ImageGenerationsRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/images/generations")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"dall-e-3\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void v1ImageEditsRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/images/edits")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"dall-e-2\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void v1ImageVariationsRouteIsReachable() {
        webTestClient.post()
            .uri("/v1/images/variations")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"dall-e-2\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void missingApiKeyReturnsUnauthorized() {
        reset(apiKeyExtractor);
        webTestClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4\"}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing API key");
    }

    @Test
    void embeddingsWithoutStreamFlagStillReturns501() {
        webTestClient.post()
            .uri("/v1/embeddings")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"input\":\"hello\"}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    @Test
    void streamFalseIsTreatedAsNonStream() {
        webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"stream\":false,\"messages\":[]}")
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501)
            .jsonPath("$.message").isEqualTo("Relay adapter is not yet wired for OPENAI_CHAT_COMPLETION");
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<?> parse(String body) {
        try {
            return objectMapper.readValue(body, ApiResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response body: " + body, e);
        }
    }
}
