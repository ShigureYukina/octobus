package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.config.DatabaseProperties;
import io.github.shigureyukina.octobus.domain.StatsAPIKey;
import io.github.shigureyukina.octobus.domain.StatsChannel;
import io.github.shigureyukina.octobus.domain.StatsDaily;
import io.github.shigureyukina.octobus.domain.StatsHourly;
import io.github.shigureyukina.octobus.domain.StatsMetrics;
import io.github.shigureyukina.octobus.domain.StatsTotal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for stats_total, stats_daily, stats_hourly, stats_apikey,
 * and stats_channel tables.
 */
@Repository
public class StatsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseProperties databaseProperties;

    public StatsRepository(JdbcTemplate jdbcTemplate, DatabaseProperties databaseProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseProperties = databaseProperties;
    }

  // ========== Reads ==========

  private static final org.springframework.jdbc.core.RowMapper<StatsAPIKey> APIKEY_MAPPER =
      (rs, rowNum) -> new StatsAPIKey(
          rs.getInt("api_key_id"),
          rs.getLong("input_token"),
          rs.getLong("output_token"),
          rs.getDouble("input_cost"),
          rs.getDouble("output_cost"),
          rs.getLong("wait_time"),
          rs.getLong("request_success"),
          rs.getLong("request_failed"),
          rs.getLong("cache_hit_token")
      );

  /** Returns all per-API-key stats rows. */
  public List<StatsAPIKey> findByApiKey() {
    return jdbcTemplate.query("SELECT * FROM stats_apikey", APIKEY_MAPPER);
  }

  /**
     * Returns the single aggregated total row, or a zero-filled default.
     */
    public StatsTotal findTotal() {
        var rows = jdbcTemplate.query(
            "SELECT id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_total WHERE id = 1",
            (rs, rowNum) -> new StatsTotal(
                rs.getInt("id"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            )
        );
        if (rows.isEmpty()) {
            return StatsTotal.of(1, StatsMetrics.zero());
        }
        return rows.get(0);
    }

    /**
     * Returns today's stats_daily row, or a zero-filled default.
     */
    public StatsDaily findToday() {
        String today = java.time.LocalDate.now().toString();
        var rows = jdbcTemplate.query(
            "SELECT `date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_daily WHERE `date` = ?",
            (rs, rowNum) -> new StatsDaily(
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            today
        );
        if (rows.isEmpty()) {
            return StatsDaily.of(today, StatsMetrics.zero());
        }
        return rows.get(0);
    }

    /**
     * Returns all daily stats records ordered by date descending.
     */
    public List<StatsDaily> findAllDaily() {
        return jdbcTemplate.query(
            "SELECT `date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_daily ORDER BY `date` DESC",
            (rs, rowNum) -> new StatsDaily(
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            )
        );
    }

    /**
     * Returns hourly stats for the last N hours ordered by date DESC, hour DESC.
     */
    public List<StatsHourly> findHourly(int hours) {
        return jdbcTemplate.query(
            "SELECT `hour`, `date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_hourly "
            + "ORDER BY `date` DESC, `hour` DESC LIMIT ?",
            (rs, rowNum) -> new StatsHourly(
                rs.getInt("hour"),
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            ),
            hours
        );
    }

    // ========== Writes (accumulative UPSERT) ==========

    /**
     * Accumulate metrics into the single stats_total row.
     */
    public void updateTotal(StatsMetrics delta) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update("""
                INSERT INTO stats_total (id, input_token, output_token, input_cost,
                    output_cost, wait_time, request_success, request_failed, cache_hit_token)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_token = input_token + VALUES(input_token),
                    output_token = output_token + VALUES(output_token),
                    input_cost = input_cost + VALUES(input_cost),
                    output_cost = output_cost + VALUES(output_cost),
                    wait_time = wait_time + VALUES(wait_time),
                    request_success = request_success + VALUES(request_success),
                    request_failed = request_failed + VALUES(request_failed),
                    cache_hit_token = cache_hit_token + VALUES(cache_hit_token)
                """,
                delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
                delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO stats_total (id, input_token, output_token, input_cost,
                output_cost, wait_time, request_success, request_failed, cache_hit_token)
            VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                input_token = stats_total.input_token + ?,
                output_token = stats_total.output_token + ?,
                input_cost = stats_total.input_cost + ?,
                output_cost = stats_total.output_cost + ?,
                wait_time = stats_total.wait_time + ?,
                request_success = stats_total.request_success + ?,
                request_failed = stats_total.request_failed + ?,
                cache_hit_token = stats_total.cache_hit_token + ?
            """,
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken(),
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
        );
    }

    /**
     * Accumulate metrics into the hourly stats row for the given hour and date.
     */
    public void updateHourly(int hour, String date, StatsMetrics delta) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update("""
                INSERT INTO stats_hourly (`hour`, `date`, input_token, output_token, input_cost,
                    output_cost, wait_time, request_success, request_failed, cache_hit_token)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_token = input_token + VALUES(input_token),
                    output_token = output_token + VALUES(output_token),
                    input_cost = input_cost + VALUES(input_cost),
                    output_cost = output_cost + VALUES(output_cost),
                    wait_time = wait_time + VALUES(wait_time),
                    request_success = request_success + VALUES(request_success),
                    request_failed = request_failed + VALUES(request_failed),
                    cache_hit_token = cache_hit_token + VALUES(cache_hit_token)
                """,
                hour, date,
                delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
                delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO stats_hourly (`hour`, `date`, input_token, output_token, input_cost,
                output_cost, wait_time, request_success, request_failed, cache_hit_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(`hour`, `date`) DO UPDATE SET
                input_token = stats_hourly.input_token + ?,
                output_token = stats_hourly.output_token + ?,
                input_cost = stats_hourly.input_cost + ?,
                output_cost = stats_hourly.output_cost + ?,
                wait_time = stats_hourly.wait_time + ?,
                request_success = stats_hourly.request_success + ?,
                request_failed = stats_hourly.request_failed + ?,
                cache_hit_token = stats_hourly.cache_hit_token + ?
            """,
            hour, date,
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken(),
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
        );
    }

    /**
     * Accumulate metrics into the daily stats row for the given date.
     */
    public void updateDaily(String date, StatsMetrics delta) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update("""
                INSERT INTO stats_daily (`date`, input_token, output_token, input_cost,
                    output_cost, wait_time, request_success, request_failed, cache_hit_token)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_token = input_token + VALUES(input_token),
                    output_token = output_token + VALUES(output_token),
                    input_cost = input_cost + VALUES(input_cost),
                    output_cost = output_cost + VALUES(output_cost),
                    wait_time = wait_time + VALUES(wait_time),
                    request_success = request_success + VALUES(request_success),
                    request_failed = request_failed + VALUES(request_failed),
                    cache_hit_token = cache_hit_token + VALUES(cache_hit_token)
                """,
                date,
                delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
                delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO stats_daily (`date`, input_token, output_token, input_cost,
                output_cost, wait_time, request_success, request_failed, cache_hit_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(`date`) DO UPDATE SET
                input_token = stats_daily.input_token + ?,
                output_token = stats_daily.output_token + ?,
                input_cost = stats_daily.input_cost + ?,
                output_cost = stats_daily.output_cost + ?,
                wait_time = stats_daily.wait_time + ?,
                request_success = stats_daily.request_success + ?,
                request_failed = stats_daily.request_failed + ?,
                cache_hit_token = stats_daily.cache_hit_token + ?
            """,
            date,
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken(),
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
        );
    }

    /**
     * Accumulate metrics into the per-API-key stats row.
     */
    public void updateAPIKey(int apiKeyId, StatsMetrics delta) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update("""
                INSERT INTO stats_apikey (api_key_id, input_token, output_token, input_cost,
                    output_cost, wait_time, request_success, request_failed, cache_hit_token)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_token = input_token + VALUES(input_token),
                    output_token = output_token + VALUES(output_token),
                    input_cost = input_cost + VALUES(input_cost),
                    output_cost = output_cost + VALUES(output_cost),
                    wait_time = wait_time + VALUES(wait_time),
                    request_success = request_success + VALUES(request_success),
                    request_failed = request_failed + VALUES(request_failed),
                    cache_hit_token = cache_hit_token + VALUES(cache_hit_token)
                """,
                apiKeyId,
                delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
                delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO stats_apikey (api_key_id, input_token, output_token, input_cost,
                output_cost, wait_time, request_success, request_failed, cache_hit_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(api_key_id) DO UPDATE SET
                input_token = stats_apikey.input_token + ?,
                output_token = stats_apikey.output_token + ?,
                input_cost = stats_apikey.input_cost + ?,
                output_cost = stats_apikey.output_cost + ?,
                wait_time = stats_apikey.wait_time + ?,
                request_success = stats_apikey.request_success + ?,
                request_failed = stats_apikey.request_failed + ?,
                cache_hit_token = stats_apikey.cache_hit_token + ?
            """,
            apiKeyId,
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken(),
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
        );
    }

    /**
     * Accumulate metrics into the per-channel stats row.
     */
    public void updateChannel(int channelId, StatsMetrics delta) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update("""
                INSERT INTO stats_channel (channel_id, input_token, output_token, input_cost,
                    output_cost, wait_time, request_success, request_failed, cache_hit_token)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    input_token = input_token + VALUES(input_token),
                    output_token = output_token + VALUES(output_token),
                    input_cost = input_cost + VALUES(input_cost),
                    output_cost = output_cost + VALUES(output_cost),
                    wait_time = wait_time + VALUES(wait_time),
                    request_success = request_success + VALUES(request_success),
                    request_failed = request_failed + VALUES(request_failed),
                    cache_hit_token = cache_hit_token + VALUES(cache_hit_token)
                """,
                channelId,
                delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
                delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
            );
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO stats_channel (channel_id, input_token, output_token, input_cost,
                output_cost, wait_time, request_success, request_failed, cache_hit_token)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(channel_id) DO UPDATE SET
                input_token = stats_channel.input_token + ?,
                output_token = stats_channel.output_token + ?,
                input_cost = stats_channel.input_cost + ?,
                output_cost = stats_channel.output_cost + ?,
                wait_time = stats_channel.wait_time + ?,
                request_success = stats_channel.request_success + ?,
                request_failed = stats_channel.request_failed + ?,
                cache_hit_token = stats_channel.cache_hit_token + ?
            """,
            channelId,
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken(),
            delta.inputToken(), delta.outputToken(), delta.inputCost(), delta.outputCost(),
            delta.waitTime(), delta.requestSuccess(), delta.requestFailed(), delta.cacheHitToken()
        );
    }

    // ========== Export helpers (unpaginated) ==========

    /**
     * Returns all hourly stats rows ordered by date DESC, hour DESC.
     */
    public List<StatsHourly> findAllHourly() {
        return jdbcTemplate.query(
            "SELECT `hour`, `date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_hourly ORDER BY `date` DESC, `hour` DESC",
            (rs, rowNum) -> new StatsHourly(
                rs.getInt("hour"),
                rs.getString("date"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            )
        );
    }

    /**
     * Returns all channel stats rows ordered by channel_id.
     */
    public List<StatsChannel> findAllChannel() {
        return jdbcTemplate.query(
            "SELECT channel_id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token "
            + "FROM stats_channel ORDER BY channel_id",
            (rs, rowNum) -> new StatsChannel(
                rs.getInt("channel_id"),
                rs.getLong("input_token"),
                rs.getLong("output_token"),
                rs.getDouble("input_cost"),
                rs.getDouble("output_cost"),
                rs.getLong("wait_time"),
                rs.getLong("request_success"),
                rs.getLong("request_failed"),
                rs.getLong("cache_hit_token")
            )
        );
    }

    /**
     * Returns channel stats as a map keyed by channel_id for efficient lookup.
     */
    public java.util.Map<Integer, StatsChannel> findChannelStatsMap() {
        var result = new java.util.HashMap<Integer, StatsChannel>();
        for (var stat : findAllChannel()) {
            result.put(stat.channelId(), stat);
        }
        return result;
    }

    // ========== Import replace helpers ==========

    /**
     * Replaces the entire stats_total row (used during backup import).
     */
    public void replaceTotal(StatsTotal row) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "REPLACE INTO stats_total (id, input_token, output_token, input_cost, output_cost, "
                + "wait_time, request_success, request_failed, cache_hit_token) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
                row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO stats_total (id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.id(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
            row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
        );
    }

    /**
     * Replaces a stats_daily row (used during backup import).
     */
    public void replaceDaily(StatsDaily row) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "REPLACE INTO stats_daily (`date`, input_token, output_token, input_cost, output_cost, "
                + "wait_time, request_success, request_failed, cache_hit_token) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.date(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
                row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO stats_daily (`date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.date(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
            row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
        );
    }

    /**
     * Replaces a stats_hourly row (used during backup import).
     */
    public void replaceHourly(StatsHourly row) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "REPLACE INTO stats_hourly (`hour`, `date`, input_token, output_token, input_cost, output_cost, "
                + "wait_time, request_success, request_failed, cache_hit_token) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.hour(), row.date(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
                row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO stats_hourly (`hour`, `date`, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.hour(), row.date(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
            row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
        );
    }

    /**
     * Replaces a stats_channel row (used during backup import).
     */
    public void replaceChannel(StatsChannel row) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "REPLACE INTO stats_channel (channel_id, input_token, output_token, input_cost, output_cost, "
                + "wait_time, request_success, request_failed, cache_hit_token) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.channelId(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
                row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO stats_channel (channel_id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.channelId(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
            row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
        );
    }

    /**
     * Replaces a stats_apikey row (used during backup import).
     */
    public void replaceApiKey(StatsAPIKey row) {
        if (databaseProperties.isMysqlLike()) {
            jdbcTemplate.update(
                "REPLACE INTO stats_apikey (api_key_id, input_token, output_token, input_cost, output_cost, "
                + "wait_time, request_success, request_failed, cache_hit_token) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.apiKeyId(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
                row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
            );
            return;
        }
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO stats_apikey (api_key_id, input_token, output_token, input_cost, output_cost, "
            + "wait_time, request_success, request_failed, cache_hit_token) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            row.apiKeyId(), row.inputToken(), row.outputToken(), row.inputCost(), row.outputCost(),
            row.waitTime(), row.requestSuccess(), row.requestFailed(), row.cacheHitToken()
        );
    }
}
