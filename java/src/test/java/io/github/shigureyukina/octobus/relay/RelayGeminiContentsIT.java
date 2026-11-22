package io.github.shigureyukina.octobus.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class RelayGeminiContentsIT {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_API_KEY = new AtomicReference<>();
    private static final AtomicInteger STREAM_REQUESTS = new AtomicInteger();

    private static HttpServer upstreamServer;
    private static String upstreamBaseUrl;
    private static volatile int responseStatus = 200;
    private static volatile String responseBody = "{}";
    private static volatile boolean streamMode;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @BeforeAll
    static void startUpstream() throws IOException {
        upstreamServer = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamServer.createContext("/v1beta/models/gemini-2.5-flash:generateContent", new GeminiHandler());
        upstreamServer.createContext("/v1beta/models/gemini-2.5-flash:streamGenerateContent", new GeminiHandler());
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
        STREAM_REQUESTS.set(0);
        streamMode = false;
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
            "gemini-upstream",
            "gemini/contents",
            1,
            "[{\"url\":\"" + upstreamBaseUrl + "\",\"delay\":0}]",
            "gemini-2.5-flash",
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
            "gemini-secret",
            0,
            0,
            0.0,
            "primary"
        );
        jdbcTemplate.update(
            "INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            "gemini-group",
            1,
            "^gemini-2.5-flash$",
            0,
            60
        );
        jdbcTemplate.update(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            1,
            1,
            1,
            "gemini-2.5-flash",
            0,
            1
        );
    }

    @Test
    void nonStreamChatCompletionsRelaysToGeminiUpstream() throws Exception {
        responseStatus = 200;
        responseBody = """
            {
              "responseId": "gemini-123",
              "modelVersion": "gemini-2.5-flash",
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "Hello from Gemini"}
                    ]
                  },
                  "finishReason": "STOP"
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 11,
                "candidatesTokenCount": 4,
                "totalTokenCount": 15
              }
            }
            """;

        String body = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "model": "gemini-2.5-flash",
                  "messages": [
                    {"role": "system", "content": "You are helpful"},
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
        assertThat(response.get("choices").get(0).get("message").get("content").asText()).isEqualTo("Hello from Gemini");
        assertThat(response.get("usage").get("prompt_tokens").asInt()).isEqualTo(11);

        JsonNode upstreamRequest = objectMapper.readTree(LAST_REQUEST_BODY.get());
        assertThat(upstreamRequest.get("systemInstruction").get("parts").get(0).get("text").asText()).isEqualTo("You are helpful");
        assertThat(upstreamRequest.get("contents").get(0).get("role").asText()).isEqualTo("user");
        assertThat(upstreamRequest.get("contents").get(0).get("parts").get(0).get("text").asText()).isEqualTo("Hello relay");
        assertThat(LAST_API_KEY.get()).isEqualTo("gemini-secret");
    }

    @Test
    void streamTrueRelaysGeminiAsOpenAiSse() {
        streamMode = true;
        responseStatus = 200;
        responseBody = String.join("\n\n",
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hel\"}]}}]}",
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"lo\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":2,\"totalTokenCount\":7}}",
            ""
        );

        FluxExchangeResult<String> result = webTestClient.post()
            .uri("/v1/chat/completions")
            .header("X-API-Key", "sk-relay-test")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("""
                {
                  "model": "gemini-2.5-flash",
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
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-gemini-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    private static final class GeminiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            LAST_API_KEY.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            STREAM_REQUESTS.incrementAndGet();

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", streamMode ? "text/event-stream" : "application/json");
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
