package io.github.shigureyukina.octobus.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.shigureyukina.octobus.common.ApiResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class RelayAnthropicMessagesIT {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_API_KEY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_VERSION = new AtomicReference<>();

    private static HttpServer upstreamServer;
    private static String upstreamBaseUrl;
    private static volatile int responseStatus = 200;
    private static volatile String responseBody = "{}";

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @BeforeAll
    static void startUpstream() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamServer.createContext("/v1/messages", new AnthropicMessagesHandler());
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
        LAST_API_KEY.set(null);
        LAST_VERSION.set(null);
        responseStatus = 200;
        responseBody = "{}";

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
            1,
            1,
            1,
            "anthropic-secret",
            0,
            0,
            0.0,
            "primary"
        );
        jdbcTemplate.update(
            "INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            "anthropic-group",
            1,
            "^claude-3-5-sonnet$",
            0,
            60
        );
        jdbcTemplate.update(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            1,
            1,
            "claude-3-5-sonnet",
            0,
            1
        );
    }

    @Test
    void nonStreamMessagesRelaysToAnthropicUpstream() throws Exception {
        responseStatus = 200;
        responseBody = """
            {
              "id": "msg_123",
              "type": "message",
              "role": "assistant",
              "model": "claude-3-5-sonnet-20240620",
              "content": [
                {"type": "text", "text": "Hello from Claude"}
              ],
              "usage": {
                "input_tokens": 11,
                "output_tokens": 4
              },
              "stop_reason": "end_turn"
            }
            """;

        String body = webTestClient.post()
            .uri("/v1/messages")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "claude-3-5-sonnet",
                  "system": "You are helpful",
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ],
                  "max_tokens": 128,
                  "temperature": 0.2
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("model").asText()).isEqualTo("claude-3-5-sonnet-20240620");
        assertThat(response.get("content").get(0).get("text").asText()).isEqualTo("Hello from Claude");

        JsonNode upstreamRequest = objectMapper.readTree(LAST_REQUEST_BODY.get());
        assertThat(upstreamRequest.get("model").asText()).isEqualTo("claude-3-5-sonnet");
        assertThat(upstreamRequest.get("system").asText()).isEqualTo("You are helpful");
        assertThat(upstreamRequest.get("messages").get(0).get("content").asText()).isEqualTo("Hello relay");
        assertThat(LAST_API_KEY.get()).isEqualTo("anthropic-secret");
        assertThat(LAST_VERSION.get()).isEqualTo("2023-06-01");
    }

    @Test
    void rejectsNonTextOnlyContent() {
        String body = webTestClient.post()
            .uri("/v1/messages")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "claude-3-5-sonnet",
                  "messages": [
                    {"role": "user", "content": [{"type": "image", "source": {}}]}
                  ]
                }
                """)
            .exchange()
            .expectStatus().isBadRequest()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<?> response = parseApiResponse(body);
        assertThat(response.code()).isEqualTo(400);
        assertThat(response.message()).contains("text content blocks");
    }

    @Test
    void mapsAnthropicRateLimitErrorStatus() {
        responseStatus = 429;
        responseBody = """
            {
              "type": "error",
              "error": {
                "type": "rate_limit_error",
                "message": "rate limit exceeded"
              }
            }
            """;

        String body = webTestClient.post()
            .uri("/v1/messages")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "claude-3-5-sonnet",
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ],
                  "max_tokens": 32
                }
                """)
            .exchange()
            .expectStatus().isEqualTo(429)
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<?> response = parseApiResponse(body);
        assertThat(response.code()).isEqualTo(429);
        assertThat(response.message()).isEqualTo("rate limit exceeded");
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<?> parseApiResponse(String body) {
        try {
            return objectMapper.readValue(body, ApiResponse.class);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static Path createTempDatabasePath() {
        try {
            Path directory = Files.createTempDirectory("octobus-relay-anthropic-test");
            return directory.resolve("octobus-test.db");
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create temp database path", exception);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class AnthropicMessagesHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            LAST_API_KEY.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            LAST_VERSION.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }
}
