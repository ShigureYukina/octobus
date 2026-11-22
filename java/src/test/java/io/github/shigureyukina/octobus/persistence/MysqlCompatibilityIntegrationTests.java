package io.github.shigureyukina.octobus.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.config.DatabaseProperties;
import io.github.shigureyukina.octobus.domain.GroupCreateRequest;
import io.github.shigureyukina.octobus.domain.Setting;
import io.github.shigureyukina.octobus.domain.StatsMetrics;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "octobus.auth.jwt-secret=test-secret",
    "spring.main.web-application-type=none",
    "server.port=0",
    "octobus.database.type=mysql",
    "octobus.database.url=jdbc:h2:mem:mysql-compat;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "octobus.database.username=sa",
    "octobus.database.password="
})
class MysqlCompatibilityIntegrationTests {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseProperties databaseProperties;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private StatsRepository statsRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Test
    void mysqlModeBootstrapsWithMysqlMigrations() throws Exception {
        assertThat(databaseProperties.type()).isEqualTo(DatabaseProperties.Type.MYSQL);
        assertThat(dataSource).isNotNull();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(tableExists("app_user")).isTrue();
        assertThat(tableExists("setting")).isTrue();
        assertThat(tableExists("groups")).isTrue();
    }

    @Test
    void settingRepositoryUpsertWorksInMysqlMode() {
        settingRepository.deleteAll();

        settingRepository.upsert("proxy_url", "http://mysql.example.com");
        settingRepository.upsert("proxy_url", "http://mysql-2.example.com");
        settingRepository.batchInsert(java.util.List.of(
            new Setting("stats_save_interval", "15"),
            new Setting("relay_log_keep_enabled", "true")
        ));

        assertThat(settingRepository.findByKey("proxy_url"))
            .get()
            .extracting(Setting::value)
            .isEqualTo("http://mysql-2.example.com");
        assertThat(settingRepository.findAll()).hasSize(3);
    }

    @Test
    void statsRepositoryAccumulatesWithMysqlUpsertSyntax() {
        jdbcTemplate.update("DELETE FROM stats_channel");
        jdbcTemplate.update("DELETE FROM stats_apikey");
        jdbcTemplate.update("DELETE FROM stats_hourly");
        jdbcTemplate.update("DELETE FROM stats_daily");
        jdbcTemplate.update("DELETE FROM stats_total");

        StatsMetrics delta1 = new StatsMetrics(10, 20, 1.5, 2.5, 30, 1, 0, 5);
        StatsMetrics delta2 = new StatsMetrics(1, 2, 0.5, 0.75, 5, 2, 1, 1);

        statsRepository.updateTotal(delta1);
        statsRepository.updateTotal(delta2);
        statsRepository.updateDaily("2026-06-01", delta1);
        statsRepository.updateDaily("2026-06-01", delta2);
        statsRepository.updateHourly(12, "2026-06-01", delta1);
        statsRepository.updateHourly(12, "2026-06-01", delta2);
        statsRepository.updateAPIKey(7, delta1);
        statsRepository.updateAPIKey(7, delta2);
        statsRepository.updateChannel(9, delta1);
        statsRepository.updateChannel(9, delta2);

        assertThat(statsRepository.findTotal().inputToken()).isEqualTo(11);
        assertThat(statsRepository.findTotal().requestSuccess()).isEqualTo(3);
        assertThat(statsRepository.findToday().inputToken()).isGreaterThanOrEqualTo(0);

        Long dailyInput = jdbcTemplate.queryForObject(
            "SELECT input_token FROM stats_daily WHERE `date` = ?",
            Long.class,
            "2026-06-01"
        );
        Long hourlyInput = jdbcTemplate.queryForObject(
            "SELECT input_token FROM stats_hourly WHERE `hour` = ? AND `date` = ?",
            Long.class,
            12,
            "2026-06-01"
        );
        Long apiKeyInput = jdbcTemplate.queryForObject(
            "SELECT input_token FROM stats_apikey WHERE api_key_id = ?",
            Long.class,
            7
        );
        Long channelInput = jdbcTemplate.queryForObject(
            "SELECT input_token FROM stats_channel WHERE channel_id = ?",
            Long.class,
            9
        );

        assertThat(dailyInput).isEqualTo(11);
        assertThat(hourlyInput).isEqualTo(11);
        assertThat(apiKeyInput).isEqualTo(11);
        assertThat(channelInput).isEqualTo(11);
    }

    @Test
    void groupRepositoryCrudWorksWithQuotedGroupsTable() {
        jdbcTemplate.update("DELETE FROM group_items");
        jdbcTemplate.update("DELETE FROM `groups`");

        GroupCreateRequest request = new GroupCreateRequest(
            "mysql-group",
            1,
            ".*",
            10,
            60,
            java.util.List.of(new GroupCreateRequest.CreateItem(101, "gpt-4", 1, 2))
        );

        var created = groupRepository.create(request);
        assertThat(created.id()).isPositive();
        assertThat(created.name()).isEqualTo("mysql-group");
        assertThat(created.items()).hasSize(1);

        var loaded = groupRepository.findById(created.id());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().items()).hasSize(1);

        groupRepository.delete(created.id());
        assertThat(groupRepository.findById(created.id())).isEmpty();
    }

    private boolean tableExists(String tableName) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (tableName.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
