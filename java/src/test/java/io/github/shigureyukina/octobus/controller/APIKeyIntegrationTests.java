package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.APIKey;
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
class APIKeyIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<APIKey>> SINGLE_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<List<APIKey>>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Void>> VOID_TYPE = new TypeReference<>() {};

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM api_key");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin",
            passwordEncoder.encode("admin")
        );
        adminToken = loginAndGetToken();
    }

    @Test
    void createAndList() {
        String createBody = webTestClient.post()
            .uri("/api/v1/apikey/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"test-key\",\"enabled\":true}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<APIKey> createResponse = parseResponse(createBody, SINGLE_TYPE);
        assertThat(createResponse.code()).isEqualTo(200);
        assertThat(createResponse.data()).isNotNull();
        assertThat(createResponse.data().name()).isEqualTo("test-key");
        assertThat(createResponse.data().apiKey()).startsWith("sk-octopus-");
        assertThat(createResponse.data().apiKey().length()).isGreaterThan("sk-octopus-".length());
        int createdId = createResponse.data().id();

        String listBody = webTestClient.get()
            .uri("/api/v1/apikey/list")
            .header("Authorization", "Bearer " + adminToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<APIKey>> listResponse = parseResponse(listBody, LIST_TYPE);
        assertThat(listResponse.code()).isEqualTo(200);
        assertThat(listResponse.data()).hasSize(1);
        assertThat(listResponse.data().getFirst().id()).isEqualTo(createdId);
        assertThat(listResponse.data().getFirst().name()).isEqualTo("test-key");
    }

    @Test
    void updateDoesNotChangeApiKey() {
        String createBody = webTestClient.post()
            .uri("/api/v1/apikey/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"original\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<APIKey> createResponse = parseResponse(createBody, SINGLE_TYPE);
        int id = createResponse.data().id();
        String originalApiKey = createResponse.data().apiKey();

        String updateBody = webTestClient.post()
            .uri("/api/v1/apikey/update")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + id + ",\"name\":\"updated\",\"enabled\":false}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<APIKey> updateResponse = parseResponse(updateBody, SINGLE_TYPE);
        assertThat(updateResponse.data().name()).isEqualTo("updated");
        assertThat(updateResponse.data().enabled()).isFalse();
        assertThat(updateResponse.data().apiKey()).isEqualTo(originalApiKey);
    }

    @Test
    void deleteRemovesKey() {
        String createBody = webTestClient.post()
            .uri("/api/v1/apikey/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"to-delete\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<APIKey> createResponse = parseResponse(createBody, SINGLE_TYPE);
        int id = createResponse.data().id();

        webTestClient.delete()
            .uri("/api/v1/apikey/delete/" + id)
            .header("Authorization", "Bearer " + adminToken)
            .exchange()
            .expectStatus().isOk();

        String listBody = webTestClient.get()
            .uri("/api/v1/apikey/list")
            .header("Authorization", "Bearer " + adminToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<APIKey>> listResponse = parseResponse(listBody, LIST_TYPE);
        assertThat(listResponse.data()).isEmpty();
    }

    @Test
    void wrongAuthReturns401() {
        webTestClient.post()
            .uri("/api/v1/apikey/create")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"no-auth\"}")
            .exchange()
            .expectStatus().isUnauthorized();

        webTestClient.get()
            .uri("/api/v1/apikey/list")
            .exchange()
            .expectStatus().isUnauthorized();

        webTestClient.post()
            .uri("/api/v1/apikey/update")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":1}")
            .exchange()
            .expectStatus().isUnauthorized();

        webTestClient.delete()
            .uri("/api/v1/apikey/delete/1")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    private String loginAndGetToken() {
        String body = webTestClient.post()
            .uri("/api/v1/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"admin\",\"expire\":0}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        var loginType = new TypeReference<ApiResponse<io.github.shigureyukina.octobus.domain.UserLoginResponse>>() {};
        ApiResponse<io.github.shigureyukina.octobus.domain.UserLoginResponse> response = parseResponse(body, loginType);
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
            return Files.createTempDirectory("octobus-apikey-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
