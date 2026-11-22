package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.Setting;
import io.github.shigureyukina.octobus.domain.UserLoginResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class SettingIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<UserLoginResponse>> LOGIN_TYPE = new TypeReference<>() {};

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
        jdbcTemplate.update("DELETE FROM setting");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin", passwordEncoder.encode("admin")
        );
        jdbcTemplate.update(
            "INSERT INTO setting (key, value) VALUES (?, ?)",
            "proxy_url", ""
        );
        jdbcTemplate.update(
            "INSERT INTO setting (key, value) VALUES (?, ?)",
            "stats_save_interval", "10"
        );
        jdbcTemplate.update(
            "INSERT INTO setting (key, value) VALUES (?, ?)",
            "relay_log_keep_enabled", "true"
        );
        authToken = loginAndGetToken("admin", "admin");
    }

    @Test
    void listReturnsAllSettings() {
        String body = webTestClient.get()
            .uri("/api/v1/setting/list")
            .header("Authorization", "Bearer " + authToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Setting>> response = parseResponse(body, new TypeReference<>() {});
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isNotNull();
        assertThat(response.data()).hasSize(3);
        assertThat(response.data()).anyMatch(s -> s.key().equals("proxy_url"));
        assertThat(response.data()).anyMatch(s -> s.key().equals("stats_save_interval"));
        assertThat(response.data()).anyMatch(s -> s.key().equals("relay_log_keep_enabled"));
    }

    @Test
    void setUpdatesExistingSetting() {
        webTestClient.post()
            .uri("/api/v1/setting/set")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"proxy_url\",\"value\":\"http://proxy.example.com\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.key").isEqualTo("proxy_url")
            .jsonPath("$.data.value").isEqualTo("http://proxy.example.com");

        String updated = jdbcTemplate.queryForObject(
            "SELECT value FROM setting WHERE key = ?", String.class, "proxy_url"
        );
        assertThat(updated).isEqualTo("http://proxy.example.com");
    }

    @Test
    void setCreatesNewSetting() {
        webTestClient.post()
            .uri("/api/v1/setting/set")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"model_info_update_interval\",\"value\":\"12\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.key").isEqualTo("model_info_update_interval")
            .jsonPath("$.data.value").isEqualTo("12");

        String updated = jdbcTemplate.queryForObject(
            "SELECT value FROM setting WHERE key = ?", String.class, "model_info_update_interval"
        );
        assertThat(updated).isEqualTo("12");
    }

    @Test
    void setRejectsUnknownKey() {
        webTestClient.post()
            .uri("/api/v1/setting/set")
            .header("Authorization", "Bearer " + authToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"key\":\"unknown_key\",\"value\":\"whatever\"}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.message").value(msg -> assertThat((String) msg).contains("Unknown setting key"));
    }

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

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-setting-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
