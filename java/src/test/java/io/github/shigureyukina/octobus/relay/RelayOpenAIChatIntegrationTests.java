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
class RelayOpenAIChatIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();

    private static HttpServer upstreamServer;
    private static String upstreamBaseUrl;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @BeforeAll
    static void startUpstream() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamServer.createContext("/v1/chat/completions", new OpenAIChatHandler());
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
    void nonStreamChatCompletionsRelaysToOpenAICompatibleUpstream() throws Exception {
        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "gpt-4o",
                  "messages": [
                    {"role": "user", "content": "Hello relay"}
                  ],
                  "temperature": 0.2
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        assertThat(body).isNotNull();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("model").asText()).isEqualTo("gpt-4o-test");
        assertThat(response.get("choices").get(0).get("message").get("content").asText()).isEqualTo("Hello from upstream");

        JsonNode upstreamRequest = objectMapper.readTree(LAST_REQUEST_BODY.get());
        assertThat(upstreamRequest.get("model").asText()).isEqualTo("gpt-4o");
        assertThat(upstreamRequest.get("messages").get(0).get("content").asText()).isEqualTo("Hello relay");
    }

    @Test
    void responsesFormatsNowRelaysViaOpenAIChatProvider() throws Exception {
        String body = webTestClient.post()
            .uri("/v1/responses")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"model\":\"gpt-4o\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        assertThat(body).isNotNull();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("model").asText()).isEqualTo("gpt-4o-test");
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
            Path directory = Files.createTempDirectory("octobus-relay-openai-test");
            return directory.resolve("octobus-test.db");
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create temp database path", exception);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class OpenAIChatHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
    }
}
