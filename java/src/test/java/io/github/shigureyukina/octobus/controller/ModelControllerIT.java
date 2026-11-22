package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.ModelInfo;
import io.github.shigureyukina.octobus.domain.OpenAIModelList;
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
class ModelControllerIT {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<ModelInfo>> SINGLE_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<List<ModelInfo>>> LIST_TYPE = new TypeReference<>() {};
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
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM groups");
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");
        jdbcTemplate.update("DELETE FROM api_key");
        jdbcTemplate.update("DELETE FROM model");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin",
            passwordEncoder.encode("admin")
        );
        adminToken = loginAndGetToken();
    }

    @Test
    void crudFlowAndModelListReflectChanges() {
        String createBody = authenticatedPost(
            "/api/v1/model/create",
            """
                {"name":"GPT-4O","input":1.0,"output":2.0,"cache_read":0.5,"cache_write":1.0}
                """
        );

        ApiResponse<ModelInfo> createResponse = parseResponse(createBody, SINGLE_TYPE);
        assertThat(createResponse.code()).isEqualTo(200);
        assertThat(createResponse.data()).isNotNull();
        assertThat(createResponse.data().name()).isEqualTo("gpt-4o");
        assertThat(createResponse.data().input()).isEqualTo(1.0);

        String listBody = authenticatedGet("/api/v1/model/list");
        ApiResponse<List<ModelInfo>> listResponse = parseResponse(listBody, LIST_TYPE);
        assertThat(listResponse.data()).extracting(ModelInfo::name).containsExactly("gpt-4o");

        String updateBody = authenticatedPost(
            "/api/v1/model/update",
            """
                {"name":"GPT-4O","input":1.5,"output":3.0}
                """
        );

        ApiResponse<ModelInfo> updateResponse = parseResponse(updateBody, SINGLE_TYPE);
        assertThat(updateResponse.code()).isEqualTo(200);
        assertThat(updateResponse.data()).isNotNull();
        assertThat(updateResponse.data().name()).isEqualTo("gpt-4o");
        assertThat(updateResponse.data().input()).isEqualTo(1.5);
        assertThat(updateResponse.data().output()).isEqualTo(3.0);
        assertThat(updateResponse.data().cacheRead()).isEqualTo(0.5);

        seedRouteForModel("gpt-4o", "openai/chat_completions");

        seedApiKey("sk-models-all", null);
        String publicListBody = webTestClient.get()
            .uri("/v1/models")
            .header("X-API-Key", "sk-models-all")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        OpenAIModelList publicList = parseJson(publicListBody, OpenAIModelList.class);
        assertThat(publicList.object()).isEqualTo("list");
        assertThat(publicList.data()).hasSize(1);
        assertThat(publicList.data().getFirst().id()).isEqualTo("gpt-4o");
        assertThat(publicList.data().getFirst().object()).isEqualTo("model");
        assertThat(publicList.data().getFirst().ownedBy()).isEqualTo("octopus");
        assertThat(publicList.data().getFirst().created()).isPositive();

        String deleteBody = authenticatedPost(
            "/api/v1/model/delete",
            """
                {"name":"GPT-4O"}
                """
        );

        ApiResponse<Void> deleteResponse = parseResponse(deleteBody, VOID_TYPE);
        assertThat(deleteResponse.code()).isEqualTo(200);

        String finalListBody = authenticatedGet("/api/v1/model/list");
        ApiResponse<List<ModelInfo>> finalListResponse = parseResponse(finalListBody, LIST_TYPE);
        assertThat(finalListResponse.data()).isEmpty();
    }

    @Test
    void duplicateCreateReturns409() {
        authenticatedPost(
            "/api/v1/model/create",
            """
                {"name":"gpt-4o","input":1.0,"output":2.0,"cache_read":0.5,"cache_write":1.0}
                """
        );

        webTestClient.post()
            .uri("/api/v1/model/create")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"gpt-4o","input":1.0,"output":2.0,"cache_read":0.5,"cache_write":1.0}
                """)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo(409)
            .jsonPath("$.message").isEqualTo("Model already exists");
    }

    @Test
    void updateAndDeleteMissingModelReturn404() {
        webTestClient.post()
            .uri("/api/v1/model/update")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"missing-model","input":1.5}
                """)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo(404)
            .jsonPath("$.message").isEqualTo("Model not found");

        webTestClient.post()
            .uri("/api/v1/model/delete")
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"missing-model"}
                """)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.code").isEqualTo(404)
            .jsonPath("$.message").isEqualTo("Model not found");
    }

    @Test
    void v1ModelsFiltersBySupportedModels() {
        jdbcTemplate.update(
            "INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "gpt-4o", 1.0, 2.0, 0.5, 1.0
        );
        jdbcTemplate.update(
            "INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "claude-3-5-sonnet", 1.2, 2.4, 0.6, 1.2
        );
        seedRouteForModel("gpt-4o", "openai/chat_completions");
        seedRouteForModel("claude-3-5-sonnet", "anthropic/messages");
        seedApiKey("sk-models-filtered", "gpt-4o");

        String body = webTestClient.get()
            .uri("/v1/models")
            .header("X-API-Key", "sk-models-filtered")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        OpenAIModelList response = parseJson(body, OpenAIModelList.class);
        assertThat(response.object()).isEqualTo("list");
        assertThat(response.data()).extracting(model -> model.id()).containsExactly("gpt-4o");
    }

    @Test
    void v1ModelsRejectsDisabledApiKey() {
        jdbcTemplate.update(
            "INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "gpt-4o", 1.0, 2.0, 0.5, 1.0
        );
        seedRouteForModel("gpt-4o", "openai/chat_completions");
        jdbcTemplate.update(
            "INSERT INTO api_key (name, api_key, enabled, expire_at, max_cost, supported_models) VALUES (?, ?, ?, ?, ?, ?)",
            "disabled-key", "disabled-key", 0, null, null, null
        );

        webTestClient.get()
            .uri("/v1/models")
            .header("X-API-Key", "disabled-key")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo(403)
            .jsonPath("$.message").isEqualTo("API key is disabled");
    }

    @Test
    void v1ModelsRejectsExpiredApiKey() {
        jdbcTemplate.update(
            "INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            "gpt-4o", 1.0, 2.0, 0.5, 1.0
        );
        seedRouteForModel("gpt-4o", "openai/chat_completions");
        jdbcTemplate.update(
            "INSERT INTO api_key (name, api_key, enabled, expire_at, max_cost, supported_models) VALUES (?, ?, ?, ?, ?, ?)",
            "expired-key", "expired-key", 1, 1L, null, null
        );

        webTestClient.get()
            .uri("/v1/models")
            .header("X-API-Key", "expired-key")
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo(403)
            .jsonPath("$.message").isEqualTo("API key has expired");
    }

    private void seedApiKey(String rawApiKey, String supportedModels) {
        jdbcTemplate.update(
            "INSERT INTO api_key (name, api_key, enabled, expire_at, max_cost, supported_models) VALUES (?, ?, ?, ?, ?, ?)",
            rawApiKey, rawApiKey, 1, null, null, supportedModels
        );
    }

    private void seedRouteForModel(String modelName, String channelType) {
        int channelId = Math.abs((modelName + channelType).hashCode());
        int keyId = channelId;
        int groupId = channelId;
        int groupItemId = channelId;
        jdbcTemplate.update(
            "INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy, auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            channelId,
            modelName + "-channel",
            channelType,
            1,
            "[{\"url\":\"https://example.test\",\"delay\":0}]",
            modelName,
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
            keyId,
            channelId,
            1,
            modelName + "-secret",
            0,
            0,
            0.0,
            "primary"
        );
        jdbcTemplate.update(
            "INSERT INTO groups (id, name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?, ?)",
            groupId,
            modelName + "-group",
            1,
            "^" + java.util.regex.Pattern.quote(modelName) + "$",
            0,
            60
        );
        jdbcTemplate.update(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            groupItemId,
            groupId,
            channelId,
            modelName,
            0,
            1
        );
    }

    private String authenticatedGet(String path) {
        return webTestClient.get()
            .uri(path)
            .header("Authorization", "Bearer " + adminToken)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();
    }

    private String authenticatedPost(String path, String body) {
        return webTestClient.post()
            .uri(path)
            .header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();
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

    private <T> T parseJson(String body, Class<T> type) {
        assertThat(body).isNotNull();
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse JSON", ex);
        }
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-model-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
