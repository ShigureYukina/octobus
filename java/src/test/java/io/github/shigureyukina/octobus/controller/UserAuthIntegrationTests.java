package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.UserLoginResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
class UserAuthIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<UserLoginResponse>> LOGIN_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<String>> STRING_TYPE = new TypeReference<>() {};

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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin",
            passwordEncoder.encode("admin")
        );
    }

    @Test
    void loginReturnsJwtAndExpireAt() {
        String body = webTestClient.post()
            .uri("/api/v1/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"admin\",\"expire\":0}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<UserLoginResponse> response = parseResponse(body, LOGIN_TYPE);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isNotNull();
        assertThat(response.data().token()).isNotBlank();
        assertThat(response.data().expireAt()).isNotBlank();
    }

    @Test
    void wrongCredentialsReturnUnauthorized() {
        webTestClient.post()
            .uri("/api/v1/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"wrong\",\"expire\":0}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    @Test
    void logoutInvalidatesCurrentToken() {
        String token = loginAndGetToken("admin", "admin");

        webTestClient.post()
            .uri("/api/v1/user/logout")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data").isEqualTo("logout successful");

        webTestClient.get()
            .uri("/api/v1/user/status")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.message").isEqualTo("Session has expired");
    }

    @Test
    void changePasswordRotatesSessionAndAcceptsNewPassword() {
        String token = loginAndGetToken("admin", "admin");

        webTestClient.post()
            .uri("/api/v1/user/change-password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"oldPassword\":\"admin\",\"newPassword\":\"new-secret\"}")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.post()
            .uri("/api/v1/user/change-password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"old_password\":\"admin\",\"new_password\":\"new-secret\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data").isEqualTo("password changed successfully");

        webTestClient.get()
            .uri("/api/v1/user/status")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized();

        webTestClient.post()
            .uri("/api/v1/user/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"admin\",\"password\":\"admin\",\"expire\":0}")
            .exchange()
            .expectStatus().isUnauthorized();

        assertThat(loginAndGetToken("admin", "new-secret")).isNotBlank();
    }

    @Test
    void changeUsernameRequiresReloginWithNewUsername() {
        String token = loginAndGetToken("admin", "admin");

        webTestClient.post()
            .uri("/api/v1/user/change-username")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"new_username\":\"root\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data").isEqualTo("username changed successfully");

        webTestClient.get()
            .uri("/api/v1/user/status")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized();

        assertThat(loginAndGetToken("root", "admin")).isNotBlank();
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
            return Files.createTempDirectory("octobus-user-auth-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
