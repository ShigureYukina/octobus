package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.config.DatabaseProperties;
import io.github.shigureyukina.octobus.domain.Setting;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseProperties databaseProperties;

    public SettingRepository(JdbcTemplate jdbcTemplate, DatabaseProperties databaseProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseProperties = databaseProperties;
    }

    public List<Setting> findAll() {
        return jdbcTemplate.query(
            "SELECT `key`, `value` FROM setting ORDER BY `key`",
            (rs, rowNum) -> new Setting(rs.getString("key"), rs.getString("value"))
        );
    }

    public Optional<Setting> findByKey(String key) {
        List<Setting> results = jdbcTemplate.query(
            "SELECT `key`, `value` FROM setting WHERE `key` = ?",
            (rs, rowNum) -> new Setting(rs.getString("key"), rs.getString("value")),
            key
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public int count() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM setting", Integer.class);
        return count != null ? count : 0;
    }

    public void upsert(String key, String value) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "INSERT INTO setting (`key`, `value`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)",
                key,
                value
            );
            return;
        }

        jdbcTemplate.update(
            "INSERT INTO setting (`key`, `value`) VALUES (?, ?) ON CONFLICT(`key`) DO UPDATE SET `value` = ?",
            key,
            value,
            value
        );
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM setting");
    }

    public void batchInsert(List<Setting> settings) {
        jdbcTemplate.batchUpdate("INSERT INTO setting (`key`, `value`) VALUES (?, ?)",
            settings, settings.size(), (ps, setting) -> {
                ps.setString(1, setting.key());
                ps.setString(2, setting.value());
            });
    }
}
