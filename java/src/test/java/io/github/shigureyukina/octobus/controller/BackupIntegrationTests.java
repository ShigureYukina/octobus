package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.DBDump;
import io.github.shigureyukina.octobus.domain.DBImportResult;
import io.github.shigureyukina.octobus.domain.ModelInfo;
import io.github.shigureyukina.octobus.domain.Setting;
import io.github.shigureyukina.octobus.domain.UserLoginResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class BackupIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<UserLoginResponse>> LOGIN_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<DBDump>> DUMP_RESPONSE_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<DBImportResult>> IMPORT_RESPONSE_TYPE = new TypeReference<>() {};

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String authToken;

    @BeforeEach
    void setUp() {
        // Clean all relevant tables
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM `groups`");
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");
        jdbcTemplate.update("DELETE FROM api_key");
        jdbcTemplate.update("DELETE FROM model");
        jdbcTemplate.update("DELETE FROM setting");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("DELETE FROM stats_total");
        jdbcTemplate.update("DELETE FROM stats_daily");
        jdbcTemplate.update("DELETE FROM stats_hourly");
        jdbcTemplate.update("DELETE FROM stats_channel");
        jdbcTemplate.update("DELETE FROM stats_apikey");
        jdbcTemplate.update("DELETE FROM relay_log");

        // Seed admin user
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin", passwordEncoder.encode("admin")
        );

        // Seed test data
        jdbcTemplate.update("INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "gpt-4", 30.0, 60.0, 15.0, 30.0);
        jdbcTemplate.update("INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "gpt-3.5-turbo", 10.0, 20.0, 5.0, 10.0);

        jdbcTemplate.update("INSERT INTO setting (key, value) VALUES (?, ?)", "proxy_url", "");
        jdbcTemplate.update("INSERT INTO setting (key, value) VALUES (?, ?)", "stats_save_interval", "10");

        jdbcTemplate.update("INSERT INTO channel (id, name, type, enabled) VALUES (?, ?, ?, ?)",
            1, "test-channel", "openai", 1);
        jdbcTemplate.update("INSERT INTO channel_key (id, channel_id, enabled, channel_key) VALUES (?, ?, ?, ?)",
            1, 1, 1, "sk-test-key");

        jdbcTemplate.update("INSERT INTO `groups` (id, name, mode) VALUES (?, ?, ?)", 1, "test-group", 0);
        jdbcTemplate.update("INSERT INTO group_items (id, group_id, channel_id, model_name) VALUES (?, ?, ?, ?)",
            1, 1, 1, "gpt-4");

        jdbcTemplate.update("INSERT INTO api_key (id, name, api_key, enabled) VALUES (?, ?, ?, ?)",
            1, "test-key", "sk-abc123", 1);

        authToken = loginAndGetToken("admin", "admin");
    }

    // ========== Export tests ==========

    @Test
    void exportCoreTablesWithoutLogsOrStats() {
        String body = webTestClient.get()
            .uri("/api/v1/setting/export?include_logs=false&include_stats=false")
            .header("Authorization", "Bearer " + authToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        var envelope = parseResponse(body, DUMP_RESPONSE_TYPE);
        assertThat(envelope.code()).isEqualTo(200);
        assertThat(envelope.data()).isNotNull();

        DBDump dump = envelope.data();
        assertThat(dump.version()).isOne();
        assertThat(dump.exportedAt()).isNotNull();
        assertThat(dump.includeLogs()).isFalse();
        assertThat(dump.includeStats()).isFalse();

        // Core tables
        assertThat(dump.llmInfos()).hasSize(2);
        assertThat(dump.settings()).hasSize(2);
        assertThat(dump.channels()).hasSize(1);
        assertThat(dump.channelKeys()).hasSize(1);
        assertThat(dump.groups()).hasSize(1);
        assertThat(dump.groupItems()).hasSize(1);
        assertThat(dump.apiKeys()).hasSize(1);

        // Optional tables should be null
        assertThat(dump.relayLogs()).isNull();
        assertThat(dump.statsTotal()).isNull();
        assertThat(dump.statsDaily()).isNull();
        assertThat(dump.statsHourly()).isNull();
        assertThat(dump.statsChannel()).isNull();
        assertThat(dump.statsApiKey()).isNull();
    }

    @Test
    void exportWithLogsAndStats() {
        // Seed a log entry and stats
        jdbcTemplate.update("""
            INSERT INTO relay_log (id, time, request_model_name, request_api_key_name, channel_id,
                channel_name, actual_model_name, input_tokens, output_tokens, ftut, use_time, cost,
                usage_recorded, request_content, response_content, error, attempts, total_attempts, cache_hit_tokens)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            1L, 1000L, "gpt-4", "test-key", 1, "test-channel", "gpt-4",
            100, 50, 0, 500, 0.003, 1, "hello", "world", "", "[]", 1, 0
        );
        jdbcTemplate.update("INSERT INTO stats_total (id, input_token, output_token, input_cost, output_cost) VALUES (1, 100, 50, 0.001, 0.002)");
        jdbcTemplate.update("INSERT INTO stats_daily (date, input_token, output_token) VALUES ('2026-06-01', 100, 50)");

        String body = webTestClient.get()
            .uri("/api/v1/setting/export?include_logs=true&include_stats=true")
            .header("Authorization", "Bearer " + authToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        var envelope = parseResponse(body, DUMP_RESPONSE_TYPE);
        assertThat(envelope.data()).isNotNull();

        DBDump dump = envelope.data();
        assertThat(dump.includeLogs()).isTrue();
        assertThat(dump.includeStats()).isTrue();
        assertThat(dump.relayLogs()).hasSize(1);
        assertThat(dump.statsTotal()).isNotEmpty();
        assertThat(dump.statsDaily()).isNotEmpty();
        assertThat(dump.statsHourly()).isNotNull();  // empty but not null (included)
    }

    // ========== Import tests ==========

    @Test
    void importFromMultipartRestoresData() {
        // Build a dump with known data
        DBDump dump = DBDump.core(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new ModelInfo("claude-3", 15.0, 75.0, 7.5, 15.0)),
            List.of(),
            List.of(new Setting("proxy_url", "http://new-proxy"))
        );

        byte[] dumpJson = toBytes(dump);

        // Upload via multipart
        var builder = new MultipartBodyBuilder();
        builder.part("file", dumpJson)
            .contentType(MediaType.APPLICATION_JSON)
            .filename("dump.json");

        String body = webTestClient.post()
            .uri("/api/v1/setting/import")
            .header("Authorization", "Bearer " + authToken)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        var envelope = parseResponse(body, IMPORT_RESPONSE_TYPE);
        assertThat(envelope.code()).isEqualTo(200);
        assertThat(envelope.data()).isNotNull();
        assertThat(envelope.data().rowsAffected()).isNotEmpty();

        // Verify import took effect
        List<String> models = jdbcTemplate.queryForList("SELECT name FROM model ORDER BY name", String.class);
        assertThat(models).contains("claude-3");

        String settingVal = jdbcTemplate.queryForObject(
            "SELECT value FROM setting WHERE key = ?", String.class, "proxy_url");
        assertThat(settingVal).isEqualTo("http://new-proxy");
    }

    @Test
    void importFromJsonBodyRestoresData() {
        DBDump dump = DBDump.core(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new ModelInfo("claude-3.5", 20.0, 80.0, 10.0, 20.0)),
            List.of(),
            List.of()
        );

        String body = webTestClient.post()
            .uri("/api/v1/setting/import")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dump)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        var envelope = parseResponse(body, IMPORT_RESPONSE_TYPE);
        assertThat(envelope.code()).isEqualTo(200);

        // Verify new model was imported
        List<String> models = jdbcTemplate.queryForList("SELECT name FROM model ORDER BY name", String.class);
        assertThat(models).contains("claude-3.5");
    }

    @Test
    void importRollsBackOnPrimaryKeyConflictInTransaction() {
        // Send a dump that WILL fail mid-transaction:
        // channel name is NOT NULL - a null name will cause SQLite constraint violation
        long modelCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM model", Long.class);

        DBDump failingDump = new DBDump(
            1, Instant.now(), false, false,
            List.of(), List.of(),
            List.of(), List.of(),
            List.of(new ModelInfo(null, 1.0, 1.0, 0.5, 0.5)),  // null PK name -> should fail
            List.of(), List.of(),
            null, null, null, null, null, null
        );

        webTestClient.post()
            .uri("/api/v1/setting/import")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(failingDump)
            .exchange()
            .expectStatus().isBadRequest();

        // Verify no data was changed
        long modelCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM model", Long.class);
        assertThat(modelCountAfter).isEqualTo(modelCountBefore);
    }

    // ========== Helpers ==========

    private String loginAndGetToken(String username, String password) {
        String body = webTestClient.post()
            .uri("/api/v1/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"expire\":0}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<UserLoginResponse> response = parseResponse(body, LOGIN_TYPE);
        assertThat(response.data()).isNotNull();
        return response.data().token();
    }

    private <T> T parseResponse(String body, TypeReference<T> type) {
        assertThat(body).isNotNull();
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse response", ex);
        }
    }

    private byte[] toBytes(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to serialize", ex);
        }
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-backup-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
