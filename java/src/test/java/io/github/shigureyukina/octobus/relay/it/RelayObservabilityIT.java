package io.github.shigureyukina.octobus.relay.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.ChannelAttempt;
import io.github.shigureyukina.octobus.domain.RelayLog;
import io.github.shigureyukina.octobus.domain.StatsAPIKey;
import io.github.shigureyukina.octobus.domain.StatsChannel;
import io.github.shigureyukina.octobus.domain.StatsDaily;
import io.github.shigureyukina.octobus.domain.StatsHourly;
import io.github.shigureyukina.octobus.domain.StatsTotal;
import io.github.shigureyukina.octobus.relay.metrics.PriceLookup;
import io.github.shigureyukina.octobus.task.LogFlushTask;
import io.github.shigureyukina.octobus.task.StatsFlushTask;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * End-to-end integration tests verifying full database side-effects of relay operations.
 *
 * <p>Tests confirm that every relay request produces correct entries in all 5 stats tables
 * ({@code stats_total}, {@code stats_daily}, {@code stats_hourly}, {@code stats_apikey},
 * {@code stats_channel}) and a {@code relay_log} row with correct request/response/attempts data.
 *
 * <p>Uses the same test infrastructure as {@link RelayEndToEndIT}: embedded {@link StubUpstream},
 * temp SQLite database, {@link WebTestClient}, and direct {@link JdbcTemplate} queries.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class RelayObservabilityIT {

    private static final Path DB_PATH = createTempDatabasePath();
    private static StubUpstream stub;
    private static final TypeReference<List<ChannelAttempt>> ATTEMPTS_TYPE = new TypeReference<>() {};

    private static final String SUCCESS_CH1 = """
        {"id":"chatcmpl-1","object":"chat.completion","created":1717200000,"model":"gpt-4o-ch1","choices":[{"index":0,"message":{"role":"assistant","content":"Hello from ch1"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15,"prompt_tokens_details":{"cached_tokens":2,"write_cached_tokens":1}}}
        """;
    private static final String ERROR_500 = """
        {"error":"upstream error"}
        """;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(DB_PATH));
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
    private PriceLookup priceLookup;

    @Autowired
    private StatsFlushTask statsFlushTask;

    @Autowired
    private LogFlushTask logFlushTask;

    @BeforeEach
    void setUp() {
        stub.reset();
        cleanAllTables();
        // Seed model prices for cost verification
        // Model prices must be seeded under the name used by group_items.model_name,
        // because PriceLookup is called with candidate.modelName() (= group_item model_name).
        seedModelPrices("gpt-4o", 1.0, 2.0, 0.5, 1.0);
        // PriceLookup keeps an in-memory cache; refresh after seeding so
        // observability assertions see the test prices immediately.
        priceLookup.refresh();
    }

    // ========================================================================
    // TC1: Successful relay — verify all 5 stats tables + relay_log + attempts
    // ========================================================================

    @Test
    @DisplayName("successful relay persists all stats and relay log")
    void successfulRelayPersistsAllStatsAndLog() throws Exception {
        // Capture date/time before the relay call to avoid midnight boundary issues
        String today = LocalDate.now().toString();
        int hour = LocalDateTime.now().getHour();

        seedApiKey(1, "sk-obs-tc1");
        seedChannel(1, "openai/chat_completions");
        seedChannelKey(1, 1, "key-1");
        seedGroup(1, "^gpt-4o$", 0);
        seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

        stub.enqueue(200, SUCCESS_CH1);

        String body = bodyFrom(post("/v1/chat/completions", "sk-obs-tc1",
            "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"));
        flushBuffers();

        // Verify HTTP 200 with correct model mapping
        JsonNode root = mapper.readTree(body);
        assertThat(root.get("model").asText()).isEqualTo("gpt-4o-ch1");

        // ---- Assert stats_total ----
        StatsTotal total = queryTotal();
        assertThat(total.inputToken()).isEqualTo(10);
        assertThat(total.outputToken()).isEqualTo(5);
        assertThat(total.cacheHitToken()).isEqualTo(2);
        assertThat(total.requestSuccess()).isEqualTo(1);
        assertThat(total.requestFailed()).isEqualTo(0);
        assertThat(total.inputCost()).isCloseTo(9.0e-6, offset(1e-10));
        assertThat(total.outputCost()).isCloseTo(10.0e-6, offset(1e-10));

        // ---- Assert stats_daily ----
        StatsDaily daily = queryDaily(today);
        assertThat(daily.inputToken()).isEqualTo(10);
        assertThat(daily.outputToken()).isEqualTo(5);
        assertThat(daily.cacheHitToken()).isEqualTo(2);
        assertThat(daily.requestSuccess()).isEqualTo(1);
        assertThat(daily.requestFailed()).isEqualTo(0);

        // ---- Assert stats_hourly ----
        StatsHourly hourly = queryHourly(hour, today);
        assertThat(hourly.inputToken()).isEqualTo(10);
        assertThat(hourly.outputToken()).isEqualTo(5);
        assertThat(hourly.cacheHitToken()).isEqualTo(2);
        assertThat(hourly.requestSuccess()).isEqualTo(1);
        assertThat(hourly.requestFailed()).isEqualTo(0);

        // ---- Assert stats_apikey ----
        StatsAPIKey apiKey = queryAPIKey(1);
        assertThat(apiKey.inputToken()).isEqualTo(10);
        assertThat(apiKey.outputToken()).isEqualTo(5);
        assertThat(apiKey.cacheHitToken()).isEqualTo(2);
        assertThat(apiKey.requestSuccess()).isEqualTo(1);
        assertThat(apiKey.requestFailed()).isEqualTo(0);

        // ---- Assert stats_channel ----
        StatsChannel channelStats = queryChannel(1);
        assertThat(channelStats.inputToken()).isEqualTo(10);
        assertThat(channelStats.outputToken()).isEqualTo(5);
        assertThat(channelStats.cacheHitToken()).isEqualTo(2);
        assertThat(channelStats.requestSuccess()).isEqualTo(1);
        assertThat(channelStats.requestFailed()).isEqualTo(0);

        // ---- Assert relay_log ----
        RelayLog log = queryLatestLog();
        assertThat(log.requestModelName()).isEqualTo("gpt-4o");
        // actualModelName comes from candidate.modelName() = group_items.model_name
        assertThat(log.actualModelName()).isEqualTo("gpt-4o");
        assertThat(log.inputTokens()).isEqualTo(10);
        assertThat(log.outputTokens()).isEqualTo(5);
        assertThat(log.cacheHitTokens()).isEqualTo(2);
        assertThat(log.cost()).isCloseTo(19.0e-6, offset(1e-10));
        assertThat(log.usageRecorded()).isTrue();
        assertThat(log.error()).isEmpty();
        assertThat(log.totalAttempts()).isEqualTo(1);

        // ---- Assert attempts JSON ----
        assertThat(log.attempts()).hasSize(1);
        assertThat(log.attempts().get(0).status()).isEqualTo("success");
    }

    // ========================================================================
    // TC2: Failed relay (500 upstream) — verify failure stats and log
    //
    // NOTE: Status string in ChannelAttempt is "failed" (from RelayRun line 234),
    // not "failure".
    // ========================================================================

    @Test
    @DisplayName("failed relay persists failure stats and log")
    void failedRelayPersistsFailureStatsAndLog() {
        seedApiKey(1, "sk-obs-tc2");
        seedChannel(1, "openai/chat_completions");
        seedChannelKey(1, 1, "key-1");
        seedGroup(1, "^gpt-4o$", 0);
        seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

        stub.enqueue(500, ERROR_500);

        bodyFrom(post("/v1/chat/completions", "sk-obs-tc2",
            "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"));
        flushBuffers();

        // ---- Assert stats_total: failure metrics, zero tokens/cost ----
        StatsTotal total = queryTotal();
        assertThat(total.requestSuccess()).isEqualTo(0);
        assertThat(total.requestFailed()).isEqualTo(1);
        assertThat(total.inputToken()).isZero();
        assertThat(total.outputToken()).isZero();
        assertThat(total.cacheHitToken()).isZero();
        assertThat(total.inputCost()).isCloseTo(0.0, offset(1e-10));
        assertThat(total.outputCost()).isCloseTo(0.0, offset(1e-10));

        // ---- Assert relay_log: failure fields ----
        RelayLog log = queryLatestLog();
        assertThat(log.inputTokens()).isZero();
        assertThat(log.outputTokens()).isZero();
        assertThat(log.cacheHitTokens()).isZero();
        assertThat(log.cost()).isCloseTo(0.0, offset(1e-10));
        assertThat(log.usageRecorded()).isFalse();
        assertThat(log.error()).isNotEmpty();
        assertThat(log.totalAttempts()).isEqualTo(1);

        // ---- Assert attempts ----
        assertThat(log.attempts()).hasSize(1);
        assertThat(log.attempts().get(0).status()).isEqualTo("failed");
    }

    // ========================================================================
    // TC3: Full candidate exhaustion (2 channels, both 500) — multi-attempt log
    // ========================================================================

    @Test
    @DisplayName("full candidate exhaustion persists multi-attempt log")
    void fullExhaustionPersistsMultiAttemptLog() {
        seedApiKey(1, "sk-obs-tc3");
        seedChannel(1, "openai/chat_completions");
        seedChannelKey(1, 1, "key-1");
        seedChannel(2, "openai/chat_completions");
        seedChannelKey(2, 2, "key-2");
        seedGroup(1, "^gpt-4o$", 0);
        seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);
        seedGroupItem(2, 1, 2, "gpt-4o", 0, 1);

        stub.enqueue(500, ERROR_500);
        stub.enqueue(500, ERROR_500);

        String body = bodyFrom(post("/v1/chat/completions", "sk-obs-tc3",
            "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"));
        flushBuffers();

        // Verify HTTP 502 Bad Gateway
        ApiResponse<?> response = parseApiResponse(body);
        assertThat(response.code()).isEqualTo(502);

        // ---- Assert stats: one failed request (not per-attempt) ----
        StatsTotal total = queryTotal();
        assertThat(total.requestFailed()).isEqualTo(1);
        assertThat(total.requestSuccess()).isZero();
        assertThat(total.inputToken()).isZero();
        assertThat(total.outputToken()).isZero();
        assertThat(total.cacheHitToken()).isZero();
        assertThat(total.inputCost()).isCloseTo(0.0, offset(1e-10));
        assertThat(total.outputCost()).isCloseTo(0.0, offset(1e-10));

        // ---- Assert relay_log: two attempts ----
        RelayLog log = queryLatestLog();
        assertThat(log.totalAttempts()).isEqualTo(2);
        assertThat(log.attempts()).hasSize(2);
        assertThat(log.attempts().get(0).status()).isEqualTo("failed");
        assertThat(log.attempts().get(1).status()).isEqualTo("failed");
        assertThat(log.inputTokens()).isZero();
        assertThat(log.outputTokens()).isZero();
        assertThat(log.cacheHitTokens()).isZero();
        assertThat(log.cost()).isCloseTo(0.0, offset(1e-10));
        assertThat(log.usageRecorded()).isFalse();
        assertThat(log.error()).isNotEmpty();
    }

    // ========================================================================
    // TC4: Cost precision — verify the three-tier formula
    // ========================================================================

    @Test
    @DisplayName("cost calculation matches expected three-tier formula")
    void costCalculationMatchesExpectedValues() throws Exception {
        seedApiKey(1, "sk-obs-cost");
        seedChannel(1, "openai/chat_completions");
        seedChannelKey(1, 1, "key-1");
        seedGroup(1, "^gpt-4o$", 0);
        seedGroupItem(1, 1, 1, "gpt-4o", 0, 1);

        stub.enqueue(200, SUCCESS_CH1);

        bodyFrom(post("/v1/chat/completions", "sk-obs-cost",
            "{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}"));
        flushBuffers();

        // Three-tier cost formula with SUCCESS_CH1 (prompt=10, completion=5, cacheRead=2, cacheWrite=1)
        // and seeded prices (input=1.0, output=2.0, cache_read=0.5, cache_write=1.0):
        //   nonCached  = 10 - 2 - 1 = 7
        //   inputCost  = (2 * 0.5 + 1 * 1.0 + 7 * 1.0) * 1e-6 = 9.0e-6
        //   outputCost = 5 * 2.0 * 1e-6 = 10.0e-6
        //   totalCost  = 9.0e-6 + 10.0e-6 = 19.0e-6

        StatsTotal total = queryTotal();
        assertThat(total.inputCost()).isCloseTo(9.0e-6, offset(1e-10));
        assertThat(total.outputCost()).isCloseTo(10.0e-6, offset(1e-10));

        RelayLog log = queryLatestLog();
        assertThat(log.cost()).isCloseTo(19.0e-6, offset(1e-10));
    }

    // ========================================================================
    // Helper methods
    // ========================================================================

    private WebTestClient.ResponseSpec post(String uri, String apiKey, String body) {
        return webTestClient.post()
            .uri(uri)
            .header("X-API-Key", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange();
    }

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

    private void flushBuffers() {
        statsFlushTask.flushOnce();
        logFlushTask.flushOnce();
    }

    // ---- DB query helpers ----

    private StatsTotal queryTotal() {
        var rows = jdbc.query(
            "SELECT id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_total WHERE id = 1",
            (rs, rowNum) -> new StatsTotal(
                rs.getInt("id"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            )
        );
        return rows.isEmpty()
            ? new StatsTotal(1, 0, 0, 0.0, 0.0, 0, 0, 0, 0)
            : rows.get(0);
    }

    private StatsDaily queryDaily(String date) {
        var rows = jdbc.query(
            "SELECT date, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_daily WHERE date = ?",
            (rs, rowNum) -> new StatsDaily(
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            date
        );
        return rows.isEmpty()
            ? new StatsDaily(date, 0, 0, 0.0, 0.0, 0, 0, 0, 0)
            : rows.get(0);
    }

    private StatsHourly queryHourly(int hour, String date) {
        var rows = jdbc.query(
            "SELECT hour, date, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_hourly WHERE hour = ? AND date = ?",
            (rs, rowNum) -> new StatsHourly(
                rs.getInt("hour"),
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            hour, date
        );
        return rows.isEmpty()
            ? new StatsHourly(hour, date, 0, 0, 0.0, 0.0, 0, 0, 0, 0)
            : rows.get(0);
    }

    private StatsAPIKey queryAPIKey(int apiKeyId) {
        var rows = jdbc.query(
            "SELECT api_key_id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_apikey WHERE api_key_id = ?",
            (rs, rowNum) -> new StatsAPIKey(
                rs.getInt("api_key_id"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            apiKeyId
        );
        return rows.isEmpty()
            ? new StatsAPIKey(apiKeyId, 0, 0, 0.0, 0.0, 0, 0, 0, 0)
            : rows.get(0);
    }

    private StatsChannel queryChannel(int channelId) {
        var rows = jdbc.query(
            "SELECT channel_id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_channel WHERE channel_id = ?",
            (rs, rowNum) -> new StatsChannel(
                rs.getInt("channel_id"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            channelId
        );
        return rows.isEmpty()
            ? new StatsChannel(channelId, 0, 0, 0.0, 0.0, 0, 0, 0, 0)
            : rows.get(0);
    }

    private RelayLog queryLatestLog() {
        var rows = jdbc.query(
            "SELECT id, time, request_model_name, request_api_key_name, channel_id, channel_name, "
            + "actual_model_name, input_tokens, output_tokens, ftut, use_time, cost, "
            + "usage_recorded, request_content, response_content, error, attempts, "
            + "total_attempts, cache_hit_tokens "
            + "FROM relay_log ORDER BY id DESC LIMIT 1",
            (rs, rowNum) -> mapRelayLog(rs)
        );
        assertThat(rows).as("relay_log should contain exactly one row").hasSize(1);
        return rows.get(0);
    }

    private RelayLog mapRelayLog(ResultSet rs) throws SQLException {
        return new RelayLog(
            rs.getLong("id"),
            rs.getLong("time"),
            rs.getString("request_model_name"),
            rs.getString("request_api_key_name"),
            rs.getInt("channel_id"),
            rs.getString("channel_name"),
            rs.getString("actual_model_name"),
            rs.getInt("input_tokens"),
            rs.getInt("output_tokens"),
            rs.getInt("ftut"),
            rs.getInt("use_time"),
            rs.getDouble("cost"),
            rs.getInt("usage_recorded") != 0,
            rs.getString("request_content"),
            rs.getString("response_content"),
            rs.getString("error"),
            parseAttempts(rs.getString("attempts")),
            rs.getInt("total_attempts"),
            rs.getInt("cache_hit_tokens")
        );
    }

    private List<ChannelAttempt> parseAttempts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, ATTEMPTS_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- Seed helpers ----

    private void seedApiKey(int id, String key) {
        jdbc.update("""
            INSERT INTO api_key (id, name, api_key, enabled, expire_at, max_cost, supported_models)
            VALUES (?, ?, ?, 1, NULL, NULL, NULL)
            """, id, "key-" + id, key);
    }

    private void seedChannel(int id, String type) {
        String urls = "[{\"url\":\"" + stub.baseUrl() + "/v1/chat/completions" + "\",\"delay\":0}]";
        jdbc.update("""
            INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy,
                auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex)
            VALUES (?, ?, ?, 1, ?, '', '', 0, 0, 0, '[]', NULL, NULL, NULL)
            """, id, "ch-" + id, type, urls);
    }

    private void seedChannelKey(int id, int channelId, String key) {
        jdbc.update("""
            INSERT INTO channel_key (id, channel_id, enabled, channel_key, status_code,
                last_use_time_stamp, total_cost, remark)
            VALUES (?, ?, 1, ?, 0, 0, 0.0, ?)
            """, id, channelId, key, "key-" + id);
    }

    private void seedGroup(int id, String regex, int sessionKeepTime) {
        jdbc.update("""
            INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time)
            VALUES (?, ?, 1, ?, 0, ?)
            """, id, "group-" + id, regex, sessionKeepTime);
    }

    private void seedGroupItem(int id, int groupId, int channelId, String modelName, int priority, int weight) {
        jdbc.update("""
            INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, groupId, channelId, modelName, priority, weight);
    }

    private void seedModelPrices(String name, double input, double output, double cacheRead, double cacheWrite) {
        jdbc.update("""
            INSERT OR IGNORE INTO model (name, input, output, cache_read, cache_write)
            VALUES (?, ?, ?, ?, ?)
            """, name, input, output, cacheRead, cacheWrite);
    }

    // ---- Cleanup ----

    private void cleanAllTables() {
        jdbc.update("DELETE FROM group_items");
        jdbc.update("DELETE FROM groups");
        jdbc.update("DELETE FROM channel_key");
        jdbc.update("DELETE FROM channel");
        jdbc.update("DELETE FROM api_key");
        jdbc.update("DELETE FROM stats_total");
        jdbc.update("DELETE FROM stats_daily");
        jdbc.update("DELETE FROM stats_hourly");
        jdbc.update("DELETE FROM stats_apikey");
        jdbc.update("DELETE FROM stats_channel");
        jdbc.update("DELETE FROM relay_log");
        jdbc.update("DELETE FROM model");
    }

    // ---- Infrastructure ----

    private static Path createTempDatabasePath() {
        try {
            Path dir = Files.createTempDirectory("octobus-relay-obs-");
            return dir.resolve("octobus-obs.db");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp database path", e);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
