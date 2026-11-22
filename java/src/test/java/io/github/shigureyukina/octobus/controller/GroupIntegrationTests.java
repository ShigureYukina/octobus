package io.github.shigureyukina.octobus.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupItem;
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
class GroupIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final TypeReference<ApiResponse<List<Group>>> GROUP_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<Group>> GROUP_TYPE = new TypeReference<>() {};
    private static final TypeReference<ApiResponse<String>> STRING_TYPE = new TypeReference<>() {};
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
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM groups");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update(
            "INSERT INTO app_user (username, password, token_version) VALUES (?, ?, 0)",
            "admin",
            passwordEncoder.encode("admin")
        );
    }

    @Test
    void listReturnsEmptyAfterSetup() {
        String body = authenticatedGet("/api/v1/group/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Group>> response = parseResponse(body, GROUP_LIST_TYPE);
        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data()).isEmpty();
    }

    @Test
    void createGroupAndList() {
        String token = loginAndGetToken();

        String createBody = webTestClient.post()
            .uri("/api/v1/group/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"test-group","mode":1,"match_regex":"chat.*","first_token_time_out":30,"session_keep_time":0}
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> createResp = parseResponse(createBody, GROUP_TYPE);
        assertThat(createResp.code()).isEqualTo(200);
        assertThat(createResp.data().name()).isEqualTo("test-group");
        assertThat(createResp.data().mode()).isEqualTo(1);
        assertThat(createResp.data().matchRegex()).isEqualTo("chat.*");
        assertThat(createResp.data().firstTokenTimeOut()).isEqualTo(30);
        assertThat(createResp.data().items()).isEmpty();

        String listBody = authenticatedGet("/api/v1/group/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Group>> listResp = parseResponse(listBody, GROUP_LIST_TYPE);
        assertThat(listResp.data()).hasSize(1);
        assertThat(listResp.data().getFirst().name()).isEqualTo("test-group");
    }

    @Test
    void createGroupWithItems() {
        String token = loginAndGetToken();

        String createBody = webTestClient.post()
            .uri("/api/v1/group/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"name":"group-with-items","mode":2,"items":[{"channel_id":1,"model_name":"gpt-4","priority":1,"weight":1}]}
                """)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> createResp = parseResponse(createBody, GROUP_TYPE);
        assertThat(createResp.code()).isEqualTo(200);
        assertThat(createResp.data().items()).hasSize(1);

        GroupItem item = createResp.data().items().getFirst();
        assertThat(item.channelId()).isEqualTo(1);
        assertThat(item.modelName()).isEqualTo("gpt-4");
        assertThat(item.priority()).isEqualTo(1);
        assertThat(item.weight()).isEqualTo(1);
    }

    @Test
    void updateGroupFields() {
        String token = loginAndGetToken();
        int groupId = createGroup(token, "update-test", 1);

        String updateBody = webTestClient.post()
            .uri("/api/v1/group/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + groupId + ",\"name\":\"updated-name\",\"mode\":2}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> updateResp = parseResponse(updateBody, GROUP_TYPE);
        assertThat(updateResp.data().name()).isEqualTo("updated-name");
        assertThat(updateResp.data().mode()).isEqualTo(2);
    }

    @Test
    void updateGroupItems() {
        String token = loginAndGetToken();
        int groupId = createGroup(token, "item-ops", 1);

        String addBody = webTestClient.post()
            .uri("/api/v1/group/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + groupId + ",\"items_to_add\":[{\"channel_id\":1,\"model_name\":\"gpt-4\",\"priority\":1,\"weight\":1},{\"channel_id\":2,\"model_name\":\"claude-3\",\"priority\":2,\"weight\":2}]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> addResp = parseResponse(addBody, GROUP_TYPE);
        assertThat(addResp.data().items()).hasSize(2);

        int itemId = addResp.data().items().stream()
            .filter(i -> i.channelId() == 1)
            .findFirst().orElseThrow().id();

        String updateItemBody = webTestClient.post()
            .uri("/api/v1/group/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + groupId + ",\"items_to_update\":[{\"id\":" + itemId + ",\"priority\":10,\"weight\":5}]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> updateItemResp = parseResponse(updateItemBody, GROUP_TYPE);
        GroupItem updatedItem = updateItemResp.data().items().stream()
            .filter(i -> i.id() == itemId)
            .findFirst().orElseThrow();
        assertThat(updatedItem.priority()).isEqualTo(10);
        assertThat(updatedItem.weight()).isEqualTo(5);

        int deleteItemId = addResp.data().items().stream()
            .filter(i -> i.channelId() == 2)
            .findFirst().orElseThrow().id();

        String deleteItemBody = webTestClient.post()
            .uri("/api/v1/group/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":" + groupId + ",\"items_to_delete\":[" + deleteItemId + "]}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> deleteItemResp = parseResponse(deleteItemBody, GROUP_TYPE);
        assertThat(deleteItemResp.data().items()).hasSize(1);
        assertThat(deleteItemResp.data().items().getFirst().id()).isEqualTo(itemId);
    }

    @Test
    void deleteGroup() {
        String token = loginAndGetToken();
        int groupId = createGroup(token, "delete-me", 1);

        String deleteBody = webTestClient.delete()
            .uri("/api/v1/group/delete/" + groupId)
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<String> deleteResp = parseResponse(deleteBody, STRING_TYPE);
        assertThat(deleteResp.code()).isEqualTo(200);
        assertThat(deleteResp.data()).isEqualTo("group deleted successfully");

        String listBody = authenticatedGet("/api/v1/group/list")
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<List<Group>> listResp = parseResponse(listBody, GROUP_LIST_TYPE);
        assertThat(listResp.data()).isEmpty();
    }

    @Test
    void deleteNonExistentGroupReturnsNotFound() {
        String token = loginAndGetToken();

        webTestClient.delete()
            .uri("/api/v1/group/delete/9999")
            .header("Authorization", "Bearer " + token)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void updateNonExistentGroupReturnsNotFound() {
        String token = loginAndGetToken();

        webTestClient.post()
            .uri("/api/v1/group/update")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"id\":9999,\"name\":\"ghost\"}")
            .exchange()
            .expectStatus().isNotFound();
    }

    private int createGroup(String token, String name, int mode) {
        String body = webTestClient.post()
            .uri("/api/v1/group/create")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\":\"" + name + "\",\"mode\":" + mode + "}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .blockFirst();

        ApiResponse<Group> response = parseResponse(body, GROUP_TYPE);
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
            return Files.createTempDirectory("octobus-group-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
