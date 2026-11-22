package io.github.shigureyukina.octobus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import io.github.shigureyukina.octobus.relay.it.StubUpstream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test for {@link ChannelSyncService}.
 *
 * <p>Verifies that syncing a channel fetches upstream models, updates the channel
 * model field, and cleans up orphaned group_items.
 */
@SpringBootTest(
    properties = "octobus.auth.jwt-secret=test-secret"
)
class ChannelSyncServiceIT {

    private static final Path DB_PATH = createTempDatabasePath();
    private static StubUpstream stub;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(DB_PATH));
    }

    @Autowired
    private ChannelSyncService channelSyncService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startStub() {
        stub = new StubUpstream();
    }

    @AfterAll
    static void stopStub() throws IOException {
        if (stub != null) stub.close();
        Files.deleteIfExists(DB_PATH);
    }

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM groups");
        jdbcTemplate.update("DELETE FROM channel_key");
        jdbcTemplate.update("DELETE FROM channel");
        stub.reset();
    }

    @Test
    void syncChannelUpdatesModel() {
        // Seed a channel with autoSync enabled, pointing to the stub
        var channelId = seedChannel("openai-ch", "openai/chat_completions", true, true, "");

        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"},{"id":"gpt-4-turbo"}]}
            """);

        channelSyncService.syncChannel(channelId);

        var updated = channelRepository.findById(channelId);
        assertThat(updated.model()).isEqualTo("gpt-4,gpt-4-turbo");
    }

    @Test
    void syncChannelCleansUpOrphanedGroupItems() {
        // Seed channel with existing model
        var channelId = seedChannel("openai-ch", "openai/chat_completions", true, true, "gpt-4,gpt-3.5-turbo");

        // Seed a group and group_items for the deleted model
        jdbcTemplate.update("INSERT INTO groups (id, name, mode) VALUES (100, 'test-group', 0)");
        jdbcTemplate.update(
            "INSERT INTO group_items (group_id, channel_id, model_name, priority, weight) VALUES (100, ?, 'gpt-3.5-turbo', 0, 0)",
            channelId
        );

        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"},{"id":"gpt-4-turbo"}]}
            """);

        channelSyncService.syncChannel(channelId);

        var updated = channelRepository.findById(channelId);
        assertThat(updated.model()).isEqualTo("gpt-4,gpt-4-turbo");

        // Verify orphaned group_item was deleted
        var remainingItems = jdbcTemplate.queryForList(
            "SELECT model_name FROM group_items WHERE channel_id = ?", channelId
        );
        assertThat(remainingItems).noneMatch(row -> "gpt-3.5-turbo".equals(row.get("model_name")));
    }

    @Test
    void syncChannelSkipsNonAutoSync() {
        var channelId = seedChannel("no-sync", "openai/chat_completions", true, false, "gpt-4");

        // No stub response configured — would fail if fetch was called
        channelSyncService.syncChannel(channelId);

        var updated = channelRepository.findById(channelId);
        assertThat(updated.model()).isEqualTo("gpt-4");
    }

    @Test
    void syncAllChannelsProcessesAutoSyncOnly() {
        seedChannel("auto-1", "openai/chat_completions", true, true, "");
        seedChannel("auto-2", "openai/chat_completions", true, true, "");
        seedChannel("manual", "openai/chat_completions", true, false, "gpt-4");

        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"}]}
            """);
        stub.enqueue(200, """
            {"data":[{"id":"gpt-4-turbo"}]}
            """);

        channelSyncService.syncAllChannels();

        var ch1 = channelRepository.findById(1);
        var ch2 = channelRepository.findById(2);
        var ch3 = channelRepository.findById(3);

        assertThat(ch1.model()).isEqualTo("gpt-4");
        assertThat(ch2.model()).isEqualTo("gpt-4-turbo");
        assertThat(ch3.model()).isEqualTo("gpt-4"); // unchanged
    }

    @Test
    void fetchModelsReturnsUpstreamList() {
        var channelId = seedChannel("fetch-test", "openai/chat_completions", true, true, "");

        stub.enqueue(200, """
            {"data":[{"id":"gpt-4"},{"id":"gpt-4-turbo"},{"id":"gpt-3.5-turbo"}]}
            """);

        var models = channelSyncService.fetchModels(channelId);

        assertThat(models).containsExactly("gpt-4", "gpt-4-turbo", "gpt-3.5-turbo");
    }

    // ---- helpers ----

    private int seedChannel(String name, String type, boolean enabled, boolean autoSync, String model) {
        String baseUrlsJson = "[{\"url\":\"" + stub.baseUrl() + "\",\"delay\":0}]";
        jdbcTemplate.update(
            "INSERT INTO channel (name, type, enabled, base_urls, model, custom_model, proxy, auto_sync, auto_group) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            name, type, enabled, baseUrlsJson, model, "", false, autoSync, 0
        );
        var id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM channel", Integer.class);

        // Seed channel key
        jdbcTemplate.update(
            "INSERT INTO channel_key (channel_id, enabled, channel_key) VALUES (?, ?, ?)",
            id, true, "sk-test"
        );

        return id;
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-sync-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
