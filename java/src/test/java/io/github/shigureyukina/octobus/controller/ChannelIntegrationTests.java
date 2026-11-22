package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
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
class ChannelIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<List<Channel>>> CHANNEL_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Channel>> CHANNEL_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Void>> VOID_TYPE = new TypeReference<>() {};
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin",
            passwordEncoder.encode("admin")
        );
    }

    @Test
    void listReturnsEmptyAfterSetup() {
        String body = authenticatedGet("/api/v1/channel/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Channel>> response = parseResponse(body, CHANNEL_LIST_TYPE);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isEmpty();
    }

    @Test
    void createChannelAndList() {
        String token = loginAndGetToken();

        String createBody = webTestClient.post()
            .uri("/api/v1/channel/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"test-channel\",\"type\":\"openai/chat_completions\",\"model\":\"gpt-4\",\"base_urls\":[{\"url\":\"https://api.openai.com\",\"delay\":0}],\"keys\":[{\"channel_key\":\"sk-test\"}],\"enabled\":true}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Channel> createResp = parseResponse(createBody, CHANNEL_TYPE);
        assertThat(createResp.code()).isEqualTo(200);
        assertThat(createResp.data().id()).isNotNull();
        assertThat(createResp.data().name()).isEqualTo("test-channel");

        String listBody = authenticatedGet("/api/v1/channel/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Channel>> listResp = parseResponse(listBody, CHANNEL_LIST_TYPE);
        assertThat(listResp.data()).hasSize(1);
        assertThat(listResp.data().get(0).name()).isEqualTo("test-channel");
        assertThat(listResp.data().get(0).keys()).hasSize(1);
    }

    @Test
    void updateChannelFields() {
        int id = createTestChannel("ch-to-update");

        String token = loginAndGetToken();
        String updateBody = webTestClient.post()
            .uri("/api/v1/channel/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + id + ",\"name\":\"updated-name\",\"model\":\"gpt-4-turbo\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Channel> updateResp = parseResponse(updateBody, CHANNEL_TYPE);
        assertThat(updateResp.data().name()).isEqualTo("updated-name");
        assertThat(updateResp.data().model()).isEqualTo("gpt-4-turbo");
    }

    @Test
    void enableDisableChannel() {
        int id = createTestChannel("ch-toggle");

        String token = loginAndGetToken();
        webTestClient.post()
            .uri("/api/v1/channel/enable")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + id + ",\"enabled\":false}")
            .exchange()
            .expectStatus().isOk();

        String listBody = authenticatedGet("/api/v1/channel/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Channel>> listResp = parseResponse(listBody, CHANNEL_LIST_TYPE);
        var channel = listResp.data().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
        assertThat(channel.enabled()).isFalse();
    }

    @Test
    void deleteChannel() {
        int id = createTestChannel("ch-to-delete");

        String token = loginAndGetToken();
        webTestClient.delete()
            .uri("/api/v1/channel/delete/" + id)
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk();

        String listBody = authenticatedGet("/api/v1/channel/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Channel>> listResp = parseResponse(listBody, CHANNEL_LIST_TYPE);
        assertThat(listResp.data()).isEmpty();
    }

    @Test
    void listRequiresAuth() {
        webTestClient.get()
            .uri("/api/v1/channel/list")
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void deleteNonExistentReturnsNotFound() {
        String token = loginAndGetToken();
        String body = webTestClient.delete()
            .uri("/api/v1/channel/delete/9999")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isNotFound()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Void> response = parseResponse(body, VOID_TYPE);
        assertThat(response.code()).isEqualTo(404);
    }

    @Test
    void manageChannelKeys() {
        int id = createTestChannel("ch-keys");

        String token = loginAndGetToken();

        // Add a key via update
        String addBody = webTestClient.post()
            .uri("/api/v1/channel/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + id + ",\"keys_to_add\":[{\"channel_key\":\"sk-new-key\",\"remark\":\"added later\"}]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Channel> addResp = parseResponse(addBody, CHANNEL_TYPE);
        assertThat(addResp.data().keys()).hasSize(2);

        // Delete first key
        int firstKeyId = addResp.data().keys().get(0).id();

        String deleteBody = webTestClient.post()
            .uri("/api/v1/channel/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + id + ",\"keys_to_delete\":[" + firstKeyId + "]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Channel> deleteResp = parseResponse(deleteBody, CHANNEL_TYPE);
        assertThat(deleteResp.data().keys()).hasSize(1);
        assertThat(deleteResp.data().keys().get(0).channelKey()).isEqualTo("sk-new-key");
    }

    // ---- helpers ----

    private int createTestChannel(String name) {
        String token = loginAndGetToken();
        String body = webTestClient.post()
            .uri("/api/v1/channel/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"" + name + "\",\"type\":\"openai/chat_completions\",\"model\":\"gpt-4\",\"base_urls\":[{\"url\":\"https://api.openai.com\",\"delay\":0}],\"keys\":[{\"channel_key\":\"sk-test\"}]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Channel> response = parseResponse(body, CHANNEL_TYPE);
        return response.data().id();
    }

    private WebTestClient.ResponseSpec authenticatedGet(String uri) {
        return webTestClient.get()
            .uri(uri)
            .header("Authorization", "Bearer " + loginAndGetToken())
            .exchange();
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
            return Files.createTempDirectory("octobus-channel-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
