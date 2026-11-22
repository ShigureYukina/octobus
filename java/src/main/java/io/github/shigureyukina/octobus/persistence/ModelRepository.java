package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.domain.ModelInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for the model pricing table.
 */
@Repository
public class ModelRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns all models ordered by name.
     */
    public List<ModelInfo> findAll() {
        return jdbcTemplate.query(
            "SELECT name, input, output, cache_read, cache_write FROM model ORDER BY name",
            (rs, rowNum) -> new ModelInfo(
                rs.getString("name"),
                rs.getDouble("input"),
                rs.getDouble("output"),
                rs.getDouble("cache_read"),
                rs.getDouble("cache_write")
            )
        );
    }

    public Optional<ModelInfo> findByName(String name) {
        return jdbcTemplate.query(
            "SELECT name, input, output, cache_read, cache_write FROM model WHERE name = ?",
            (rs, rowNum) -> new ModelInfo(
                rs.getString("name"),
                rs.getDouble("input"),
                rs.getDouble("output"),
                rs.getDouble("cache_read"),
                rs.getDouble("cache_write")
            ),
            name
        ).stream().findFirst();
    }

    public boolean existsByName(String name) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM model WHERE name = ?",
            Integer.class,
            name
        );
        return count != null && count > 0;
    }

    public void create(String name, double input, double output, double cacheRead, double cacheWrite) {
        jdbcTemplate.update(
            "INSERT INTO model (name, input, output, cache_read, cache_write) VALUES (?, ?, ?, ?, ?)",
            name, input, output, cacheRead, cacheWrite
        );
    }

    public void update(String name, Double input, Double output, Double cacheRead, Double cacheWrite) {
        StringBuilder sql = new StringBuilder("UPDATE model SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;

        if (input != null) {
            sql.append("input = ?");
            args.add(input);
            first = false;
        }
        if (output != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("output = ?");
            args.add(output);
            first = false;
        }
        if (cacheRead != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("cache_read = ?");
            args.add(cacheRead);
            first = false;
        }
        if (cacheWrite != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("cache_write = ?");
            args.add(cacheWrite);
            first = false;
        }
        if (first) {
            return;
        }

        sql.append(" WHERE name = ?");
        args.add(name);
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public void deleteByName(String name) {
        jdbcTemplate.update("DELETE FROM model WHERE name = ?", name);
    }

    public List<String> findAllNames() {
        return jdbcTemplate.queryForList("SELECT name FROM model ORDER BY name", String.class);
    }

    public List<ModelInfo> findByChannel(String channelType) {
        return findAll();
    }

    public void upsert(String name, double input, double output, double cacheRead, double cacheWrite) {
        if (existsByName(name)) {
            update(name, input, output, cacheRead, cacheWrite);
        } else {
            create(name, input, output, cacheRead, cacheWrite);
        }
    }
}
