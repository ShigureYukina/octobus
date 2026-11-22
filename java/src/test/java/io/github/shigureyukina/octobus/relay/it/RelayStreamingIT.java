package io.github.shigureyukina.octobus.relay.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class RelayStreamingIT {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();
    private static final AtomicInteger STREAM_REQUESTS = new AtomicInteger();
    private static volatile boolean returnErrorStatus;

    private static HttpServer upstreamServer;
    private static String upstreamBaseUrl;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @BeforeAll
    static void startUpstream() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamServer.createContext("/v1/chat/completions", new StreamingChatHandler());
        upstreamServer.start();
        upstreamBaseUrl = "http://127.0.0.1:" + upstreamServer.getAddress().getPort();
    }

    @AfterAll
    static void stopUpstream() {
        if (upstreamServer != null) {
            upstreamServer.stop(0);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LAST_REQUEST_BODY.set(null);
        STREAM_REQUESTS.set(0);
        returnErrorStatus = false;
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM groups");
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");
        jdbcTemplate.update("DELETE FROM api_key");

        jdbcTemplate.update(
            "INSERT INTO api_key (id, name, api_key, enabled, expire_at, max_cost, supported_models) VALUES (?, ?, ?, ?, ?, ?, ?)",
            1,
            "relay-key",
            "sk-relay-test",
            1,
            null,
            null,
            null
        );
        jdbcTemplate.update(
            "INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy, auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            1,
            "openai-upstream",
            "openai/chat_completions",
            1,
            "[{\"url\":\"" + upstreamBaseUrl + "\",\"delay\":0}]",
            "gpt-4o",
            "",
            0,
            0,
            0,
            "[]",
            null,
            null,
            null
        );
        jdbcTemplate.update(
            "INSERT INTO channel_key (id, channel_id, enabled, channel_key, status_code, last_use_time_stamp, total_cost, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            1,
            1,
            1,
            "upstream-secret",
            0,
            0,
            0.0,
            "primary"
        );
        jdbcTemplate.update(
            "INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            "chat-group",
            1,
            "^gpt-4o$",
            0,
            60
        );
        jdbcTemplate.update(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            1,
            1,
            "gpt-4o",
            0,
            1
        );
    }

    @Test
    void streamTrueRelaysSseEventsInOrder() throws Exception {
        FluxExchangeResult<String> result = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("""
                {
                  "model": "gpt-4o",
                  "stream": true,
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String.class);

        List<String> chunks = result.getResponseBody().collectList().block();
        String body = String.join("", chunks);
        assertThat(body).contains("\"delta\":{\"content\":\"Hel\"}");
        assertThat(body).contains("\"delta\":{\"content\":\"lo\"}");
        assertThat(body).contains("[DONE]");

        assertThat(STREAM_REQUESTS.get()).isEqualTo(1);
        assertThat(objectMapper.readTree(LAST_REQUEST_BODY.get()).path("stream").asBoolean()).isTrue();
    }

    @Test
    void clientCancelDoesNotBreakFollowUpRequests() {
        FluxExchangeResult<String> result = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("{\"model\":\"gpt-4o\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"cancel me\"}]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class);

        StepVerifier.create(result.getResponseBody().take(1))
            .expectNextMatches(chunk -> chunk.contains("Hel"))
            .thenCancel()
            .verify();

        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "gpt-4o",
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        assertThat(body).contains("Hello from upstream");
    }

    @Test
    void streamFalseStillUsesNonStreamingPath() {
        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "gpt-4o",
                  "stream": false,
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        assertThat(body).contains("Hello from upstream");
    }

    @Test
    void upstreamStreamingFailureReturnsErrorStatus() {
        returnErrorStatus = true;

        webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("""
                {
                  "model": "gpt-4o",
                  "stream": true,
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isEqualTo(502);
    }

    @Test
    void anthropicStreamingRequestReturns501() {
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM groups");
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");

        jdbcTemplate.update(
            "INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy, auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            2,
            "anthropic-upstream",
            "anthropic/messages",
            1,
            "[{\"url\":\"" + upstreamBaseUrl + "\",\"delay\":0}]",
            "claude-3-5-sonnet",
            "",
            0,
            0,
            0,
            "[]",
            null,
            null,
            null
        );
        jdbcTemplate.update(
            "INSERT INTO channel_key (id, channel_id, enabled, channel_key, status_code, last_use_time_stamp, total_cost, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            2,
            2,
            1,
            "anthropic-secret",
            0,
            0,
            0.0,
            "primary"
        );
        jdbcTemplate.update(
            "INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?, ?)",
            2,
            "anthropic-group",
            1,
            "^claude-3-5-sonnet$",
            0,
            60
        );
        jdbcTemplate.update(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            2,
            2,
            2,
            "claude-3-5-sonnet",
            0,
            1
        );

        webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("""
                {
                  "model": "claude-3-5-sonnet",
                  "stream": true,
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isEqualTo(501)
            .expectBody()
            .jsonPath("$.code").isEqualTo(501);
    }

    private static Path createTempDatabasePath() {
        try {
            Path directory = Files.createTempDirectory("octobus-relay-stream-test");
            return directory.resolve("octobus-test.db");
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create temp database path", exception);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class StreamingChatHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            STREAM_REQUESTS.incrementAndGet();

            boolean stream = LAST_REQUEST_BODY.get() != null && LAST_REQUEST_BODY.get().contains("\"stream\":true");
            if (!stream) {
                writeJsonResponse(exchange);
                return;
            }
            if (returnErrorStatus) {
                byte[] bytes = "{\"error\":\"upstream failure\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(502, bytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(bytes);
                }
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/event-stream");
            headers.add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                writeChunk(outputStream, "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}");
                sleepQuietly(50);
                outputStream.write("\n\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                writeChunk(outputStream, "data: {\"id\":\"chatcmpl-2\",\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}");
                outputStream.write("\n\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();

                sleepQuietly(50);
                writeChunk(outputStream, "data: [DONE]");
                outputStream.write("\n\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
        }

        private void writeJsonResponse(HttpExchange exchange) throws IOException {
            byte[] body = """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1717200000,
                  "model": "gpt-4o-test",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello from upstream"
                      },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 4,
                    "total_tokens": 14
                  }
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        private void writeChunk(OutputStream outputStream, String value) throws IOException {
            outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }

        private void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
