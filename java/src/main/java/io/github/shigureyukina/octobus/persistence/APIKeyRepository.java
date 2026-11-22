package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.domain.APIKey;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class APIKeyRepository {

    private final JdbcTemplate jdbcTemplate;

    public APIKeyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public APIKey save(APIKey key) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO api_key (name, api_key, enabled, expire_at, max_cost, supported_models) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, key.name());
            ps.setString(2, key.apiKey());
            ps.setInt(3, key.enabled() ? 1 : 0);
            if (key.expireAt() != null) {
                ps.setLong(4, key.expireAt());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            if (key.maxCost() != null) {
                ps.setDouble(5, key.maxCost());
            } else {
                ps.setNull(5, java.sql.Types.REAL);
            }
            if (key.supportedModels() != null) {
                ps.setString(6, key.supportedModels());
            } else {
                ps.setNull(6, java.sql.Types.VARCHAR);
            }
            return ps;
        }, keyHolder);

        int id = keyHolder.getKey() != null ? keyHolder.getKey().intValue() : 0;
        return findById(id).orElseThrow(() -> new IllegalStateException("Failed to retrieve saved API key"));
    }

    public void update(APIKey key) {
        jdbcTemplate.update(
            "UPDATE api_key SET name = ?, enabled = ?, expire_at = ?, max_cost = ?, supported_models = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            key.name(),
            key.enabled() ? 1 : 0,
            key.expireAt() != null ? key.expireAt() : null,
            key.maxCost() != null ? key.maxCost() : null,
            key.supportedModels() != null ? key.supportedModels() : null,
            key.id()
        );
    }

    public Optional<APIKey> findById(int id) {
        List<APIKey> keys = jdbcTemplate.query(
            "SELECT id, name, api_key, enabled, expire_at, max_cost, supported_models FROM api_key WHERE id = ?",
            this::mapRow,
            id
        );
        return keys.stream().findFirst();
    }

    public List<APIKey> findAll() {
        return jdbcTemplate.query(
            "SELECT id, name, api_key, enabled, expire_at, max_cost, supported_models FROM api_key ORDER BY id",
            this::mapRow
        );
    }

    public Optional<APIKey> findByApiKey(String rawApiKey) {
        List<APIKey> keys = jdbcTemplate.query(
            "SELECT id, name, api_key, enabled, expire_at, max_cost, supported_models FROM api_key WHERE api_key = ?",
            this::mapRow,
            rawApiKey
        );
        return keys.stream().findFirst();
    }

    public boolean deleteById(int id) {
        int affected = jdbcTemplate.update("DELETE FROM api_key WHERE id = ?", id);
        return affected > 0;
    }

    /**
     * Checks if an API key with the given ID exists.
     */
    public boolean existsById(int id) {
        var count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM api_key WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * Inserts an API key row from backup with a specific ID.
     */
    public void insertFromBackup(APIKey key) {
        jdbcTemplate.update(
            "INSERT INTO api_key (id, name, api_key, enabled, expire_at, max_cost, supported_models) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
            key.id(), key.name(), key.apiKey(), key.enabled() ? 1 : 0,
            key.expireAt(), key.maxCost(), key.supportedModels()
        );
    }

    private APIKey mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long expireAt = rs.getLong("expire_at");
        double maxCost = rs.getDouble("max_cost");
        return new APIKey(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("api_key"),
            rs.getInt("enabled") == 1,
            rs.wasNull() ? null : expireAt,
            rs.wasNull() ? null : maxCost,
            rs.getString("supported_models")
        );
    }
}
