package io.github.shigureyukina.octobus.relay.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end integration tests for the Milestone 3 relay pipeline.
 *
 * <p>Drives the real Spring WebFlux routes ({@code /v1/chat/completions})
 * through {@link WebTestClient} and uses an embedded {@link StubUpstream}
 * as the configured provider.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class RelayEndToEndIT {

    private static final Path DB_PATH = createTempDatabasePath();
    private static StubUpstream stub;

    private static final String SUCCESS_CH1 = """
        {"id":"chatcmpl-1","object":"chat.completion","created":1717200000,"model":"gpt-4o-ch1","choices":[{"index":0,"message":{"role":"assistant","content":"Hello from ch1"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"prompt_tokens_details":{"cached_tokens":2,"write_cached_tokens":1}}}
        """;
    private static final String SUCCESS_CH2 = """
        {"id":"chatcmpl-2","object":"chat.completion","created":1717200000,"model":"gpt-4o-ch2","choices":[{"index":0,"message":{"role":"assistant","content":"Hello from ch2"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":3,"total_tokens":13}}
        """;
    private static final String ERROR_429 = """
        {"error":"rate limited"}
        """;
    private static final String ERROR_500 = """
        {"error":"upstream error"}
        """;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(DB_PATH));
        registry.add("octobus.relay.circuit-breaker.failure-threshold", () -> "2");
        registry.add("octobus.relay.circuit-breaker.open-state-duration", () -> "3s");
        registry.add("octobus.relay.circuit-breaker.max-cooldown", () -> "6s");
    }

    @BeforeAll
    static void startStub() {
        stub = new StubUpstream();
    }

    @AfterAll
    static void stopStub() {
        if (stub != null) stub.close();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private io.github.shigureyukina.octobus.relay.breaker.CircuitBreaker circuitBreaker;

    @Autowired
    private StickyStore stickyStore;

    @BeforeEach
    void cleanDb() {
        stub.reset();
        circuitBreaker.resetAll();
        stickyStore.clear();
        jdbc.update("DELETE FROM group_items");
        jdbc.update("DELETE FROM groups");
        jdbc.update("DELETE FROM channel_key");
        jdbc.update("DELETE FROM channel");
        jdbc.update("DELETE FROM api_key");
    }

    @AfterEach
    void drainStub() {
    }

    // =============== Success path ===============

    @Nested
    @DisplayName("Success path")
    class SuccessPath {

        @Test
        @DisplayName("single candidate relay returns upstream response")
        void singleCandidate() throws Exception {
            seedApiKey(1, "sk-e2e-success");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedGroup(1, "^gpt-4o$", 60);
            seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

            stub.enqueue(200, SUCCESS_CH1);

            WebTestClient.ResponseSpec spec = webTestClient.post()
                .uri("/v1/chat/completions")
                .header("X-API-Key", "sk-e2e-success")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]}
                    """)
                .exchange();

            String body = spec.expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();

            assertThat(body).isNotNull();
            JsonNode root = mapper.readTree(body);
            assertThat(root.get("model")).isNotNull();
            assertThat(root.get("model").asText()).isEqualTo("gpt-4o-ch1");
            assertThat(root.path("choices").get(0).path("message").path("content").asText())
                .isEqualTo("Hello from ch1");
        }
    }

    // =============== Candidate failover ===============

    @Nested
    @DisplayName("Candidate failover")
    class Failover {

        @Test
        @DisplayName("first candidate 429 -> second candidate succeeds")
        void failoverOnRateLimit() throws Exception {
            seedApiKey(1, "sk-e2e-failover");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedChannel(2, "openai/chat_completions");
            seedChannelKey(2, 2, "key-2");
            // failover mode = 3, priority ascending
            seedGroup(1, "^gpt-4o$", 60, 3);
            seedGroupItem(1, 1, 1, "gpt-4o", 2, 1); // priority 2 = tried second
            seedGroupItem(2, 1, 2, "gpt-4o", 1, 1); // priority 1 = tried first

            stub.enqueue(429, ERROR_429);
            stub.enqueue(200, SUCCESS_CH1);

            String body = bodyFrom(post("/v1/chat/completions", "sk-e2e-failover",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]}
                """));

            JsonNode root = mapper.readTree(body);
            assertThat(root.get("model")).isNotNull();
            assertThat(root.get("model").asText()).isEqualTo("gpt-4o-ch1");
        }
    }

    // =============== Full candidate exhaustion ===============

    @Nested
    @DisplayName("Full candidate exhaustion")
    class Exhaustion {

        @Test
        @DisplayName("all candidates fail -> 502 Bad Gateway")
        void allFail() throws Exception {
            seedApiKey(1, "sk-e2e-exhaust");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedChannel(2, "openai/chat_completions");
            seedChannelKey(2, 2, "key-2");
            seedGroup(1, "^gpt-4o$", 0);
            seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);
            seedGroupItem(2, 1, 2, "gpt-4o", 0, 1);

            stub.enqueue(500, ERROR_500);
            stub.enqueue(500, ERROR_500);

            String body = bodyFrom(post("/v1/chat/completions", "sk-e2e-exhaust",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}]}
                """));

            ApiResponse<?> response = parseApiResponse(body);
            assertThat(response.code()).isEqualTo(502);
        }
    }

    // =============== Sticky session reuse ===============

    @Nested
    @DisplayName("Sticky session reuse")
    class StickySession {

        @Test
        @DisplayName("successful candidate is reused on second request")
        void stickyReuse() throws Exception {
            seedApiKey(1, "sk-e2e-sticky");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedChannel(2, "openai/chat_completions");
            seedChannelKey(2, 2, "key-2");
            // failover mode = 3
            seedGroup(1, "^gpt-4o$", 60, 3);
            seedGroupItem(1, 1, 1, "gpt-4o", 2, 1);
            seedGroupItem(2, 1, 2, "gpt-4o", 1, 1);

            stub.enqueue(429, ERROR_429);
            stub.enqueue(200, SUCCESS_CH1);

            String body1 = bodyFrom(post("/v1/chat/completions", "sk-e2e-sticky",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"First"}]}
                """));
            JsonNode r1 = mapper.readTree(body1);
            assertThat(r1.get("model").asText()).isEqualTo("gpt-4o-ch1");

            // second request: sticky should prefer ch1
            stub.enqueue(200, SUCCESS_CH1);

            String body2 = bodyFrom(post("/v1/chat/completions", "sk-e2e-sticky",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Second"}]}
                """));
            JsonNode r2 = mapper.readTree(body2);
            assertThat(r2.get("model").asText()).isEqualTo("gpt-4o-ch1");
        }
    }

    // =============== Circuit breaker transition ===============

    @Nested
    @DisplayName("Circuit breaker transitions")
    class CircuitBreaker {

        @Test
        @DisplayName("open -> half-open -> closed cycle")
        void breakerCycle() throws Exception {
            // failure threshold = 2 (from DynamicPropertySource), 1 channel
            seedApiKey(1, "sk-e2e-cb");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedGroup(1, "^gpt-4o$", 0);
            seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

            // request 1: fails (1 consecutive failure)
            stub.enqueue(500, ERROR_500);
            String body1 = bodyFrom(post("/v1/chat/completions", "sk-e2e-cb",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Fail 1"}]}
                """));
            assertThat(errorCodeFrom(body1)).isEqualTo(502);

            // request 2: fails (2 consecutive failures -> breaker OPENS)
            stub.enqueue(500, ERROR_500);
            String body2 = bodyFrom(post("/v1/chat/completions", "sk-e2e-cb",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Fail 2"}]}
                """));
            assertThat(errorCodeFrom(body2)).isEqualTo(502);

            // request 3: breaker open -> candidate skipped -> 502
            String body3 = bodyFrom(post("/v1/chat/completions", "sk-e2e-cb",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Open"}]}
                """));
            assertThat(errorCodeFrom(body3)).isEqualTo(502);

            // wait for cooldown (open-state-duration = 3s)
            Thread.sleep(3500);

            // request 4: half-open probe -> success -> breaker closes
            stub.enqueue(200, SUCCESS_CH1);
            String body4 = bodyFrom(post("/v1/chat/completions", "sk-e2e-cb",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Probe"}]}
                """));
            JsonNode r4 = mapper.readTree(body4);
            assertThat(r4.get("model").asText()).isEqualTo("gpt-4o-ch1");
        }
    }

    // =============== Authentication failure ===============

    @Nested
    @DisplayName("Authentication failures")
    class Auth {

        @Test
        @DisplayName("missing API key -> 401")
        void missingApiKey() {
            webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}")
                .exchange()
                .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("invalid API key -> 401")
        void invalidApiKey() {
            webTestClient.post()
                .uri("/v1/chat/completions")
                .header("X-API-Key", "sk-invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}")
                .exchange()
                .expectStatus().isUnauthorized();
        }
    }

    // =============== Stream request guard ===============

    @Nested
    @DisplayName("Stream request guard")
    class StreamGuard {

        @Test
        @DisplayName("stream=true returns 501")
        void streamReturnsNotImplemented() {
            seedApiKey(1, "sk-e2e-stream");
            seedChannel(1, "openai/chat_completions");
            seedChannelKey(1, 1, "key-1");
            seedGroup(1, "^gpt-4o$", 0);
            seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

            stub.enqueue(200, SUCCESS_CH1);

            String body = bodyFrom(post("/v1/chat/completions", "sk-e2e-stream",
                """
                {"model":"gpt-4o","messages":[{"role":"user","content":"Hello"}],"stream":true}
                """));

            ApiResponse<?> response = parseApiResponse(body);
            assertThat(response.code()).isEqualTo(501);
            assertThat(response.message()).containsIgnoringCase("not yet supported");
        }
    }

    // =============== Helper methods ===============

    /** Post a request and return only the body string (regardless of HTTP status). */
    private WebTestClient.ResponseSpec post(String uri, String apiKey, String body) {
        return webTestClient.post()
            .uri(uri)
            .header("X-API-Key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange();
    }

    /** Extract body string from a response spec. */
    private String bodyFrom(WebTestClient.ResponseSpec spec) {
        return spec.returnResult(String.class)
            .getResponseBody()
            .blockFirst();
    }

    @SuppressWarnings("unchecked")
    private ApiResponse<?> parseApiResponse(String body) {
        try {
            return mapper.readValue(body, ApiResponse.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int errorCodeFrom(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.has("code")) return root.get("code").asInt();
        } catch (Exception ignored) {
        }
        return -1;
    }

    // ---- seed helpers ----

    private void seedApiKey(int id, String key) {
        jdbc.update("""
            INSERT INTO api_key (id, name, api_key, enabled, expire_at, max_cost, supported_models)
            VALUES (?, ?, ?, 1, NULL, NULL, NULL)
            """, id, "key-" + id, key);
    }

    private void seedChannel(int id, String type) {
        String urls = "[{\"url\":\"" + stub.baseUrl() + "/v1/chat/completions" + "\",\"delay\":0}]";
        jdbc.update("""
            INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy, auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex)
            VALUES (?, ?, ?, 1, ?, '', '', 0, 0, 0, '[]', NULL, NULL, NULL)
            """, id, "ch-" + id, type, urls);
    }

    private void seedChannelKey(int id, int channelId, String key) {
        jdbc.update("""
            INSERT INTO channel_key (id, channel_id, enabled, channel_key, status_code, last_use_time_stamp, total_cost, remark)
            VALUES (?, ?, 1, ?, 0, 0, 0.0, ?)
            """, id, channelId, key, "key-" + id);
    }

    private void seedGroup(int id, String regex, int sessionKeepTime) {
        seedGroup(id, regex, sessionKeepTime, 1);
    }

    private void seedGroup(int id, String regex, int sessionKeepTime, int mode) {
        jdbc.update("""
            INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time)
            VALUES (?, ?, ?, ?, 0, ?)
            """, id, "group-" + id, mode, regex, sessionKeepTime);
    }

    private void seedGroupItem(int id, int groupId, int channelId, String modelName, int priority, int weight) {
        jdbc.update("""
            INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, groupId, channelId, modelName, priority, weight);
    }

    // ---- infrastructure ----

    private static Path createTempDatabasePath() {
        try {
            Path dir = Files.createTempDirectory("octobus-relay-e2e-");
            return dir.resolve("octobus-e2e.db");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp database path", e);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
