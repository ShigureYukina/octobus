package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.domain.BaseUrl;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.domain.CustomHeader;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ChannelRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ChannelRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public List<Channel> findAll() {
        var channels = jdbcTemplate.query(
            "SELECT id, name, type, enabled, base_urls, model, custom_model, proxy, "
            + "auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex "
            + "FROM channel ORDER BY id",
            (rs, rowNum) -> new Channel(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getBoolean("enabled"),
                parseBaseUrls(rs.getString("base_urls")),
                List.of(),
                rs.getString("model"),
                rs.getString("custom_model"),
                rs.getBoolean("proxy"),
                rs.getBoolean("auto_sync"),
                rs.getInt("auto_group"),
                parseCustomHeaders(rs.getString("custom_header")),
                rs.getString("param_override"),
                rs.getString("channel_proxy"),
                rs.getString("match_regex"),
                null
            )
        );
        for (var ch : channels) {
            var keys = findKeysByChannelId(ch.id());
            channels.set(channels.indexOf(ch), new Channel(
                ch.id(), ch.name(), ch.type(), ch.enabled(), ch.baseUrls(), keys,
                ch.model(), ch.customModel(), ch.proxy(), ch.autoSync(), ch.autoGroup(),
                ch.customHeader(), ch.paramOverride(), ch.channelProxy(), ch.matchRegex(),
                null
            ));
        }
        return channels;
    }

    public Channel findById(int id) {
        var ch = jdbcTemplate.queryForObject(
            "SELECT id, name, type, enabled, base_urls, model, custom_model, proxy, "
            + "auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex "
            + "FROM channel WHERE id = ?",
            (rs, rowNum) -> new Channel(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getBoolean("enabled"),
                parseBaseUrls(rs.getString("base_urls")),
                List.of(),
                rs.getString("model"),
                rs.getString("custom_model"),
                rs.getBoolean("proxy"),
                rs.getBoolean("auto_sync"),
                rs.getInt("auto_group"),
                parseCustomHeaders(rs.getString("custom_header")),
                rs.getString("param_override"),
                rs.getString("channel_proxy"),
                rs.getString("match_regex"),
                null
            ),
            id
        );
        var keys = findKeysByChannelId(id);
        return new Channel(
            ch.id(), ch.name(), ch.type(), ch.enabled(), ch.baseUrls(), keys,
            ch.model(), ch.customModel(), ch.proxy(), ch.autoSync(), ch.autoGroup(),
            ch.customHeader(), ch.paramOverride(), ch.channelProxy(), ch.matchRegex(),
            null
        );
    }

    public int create(String name, String type, boolean enabled, String baseUrls, String model,
                      String customModel, boolean proxy, boolean autoSync, int autoGroup,
                      String customHeader, String paramOverride, String channelProxy, String matchRegex) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO channel (name, type, enabled, base_urls, model, custom_model, proxy, "
                + "auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setBoolean(3, enabled);
            ps.setString(4, baseUrls);
            ps.setString(5, model);
            ps.setString(6, customModel);
            ps.setBoolean(7, proxy);
            ps.setBoolean(8, autoSync);
            ps.setInt(9, autoGroup);
            ps.setString(10, customHeader);
            ps.setString(11, paramOverride);
            ps.setString(12, channelProxy);
            ps.setString(13, matchRegex);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void update(int id, String name, String type, Boolean enabled, String baseUrls,
                       String model, String customModel, Boolean proxy, Boolean autoSync,
                       Integer autoGroup, String customHeader, String paramOverride,
                       String channelProxy, String matchRegex) {
        var sql = new StringBuilder("UPDATE channel SET ");
        var args = new java.util.ArrayList<>();
        boolean first = true;
        if (name != null) { sql.append("name = ?"); args.add(name); first = false; }
        if (type != null) { if (!first) sql.append(", "); sql.append("type = ?"); args.add(type); first = false; }
        if (enabled != null) { if (!first) sql.append(", "); sql.append("enabled = ?"); args.add(enabled); first = false; }
        if (baseUrls != null) { if (!first) sql.append(", "); sql.append("base_urls = ?"); args.add(baseUrls); first = false; }
        if (model != null) { if (!first) sql.append(", "); sql.append("model = ?"); args.add(model); first = false; }
        if (customModel != null) { if (!first) sql.append(", "); sql.append("custom_model = ?"); args.add(customModel); first = false; }
        if (proxy != null) { if (!first) sql.append(", "); sql.append("proxy = ?"); args.add(proxy); first = false; }
        if (autoSync != null) { if (!first) sql.append(", "); sql.append("auto_sync = ?"); args.add(autoSync); first = false; }
        if (autoGroup != null) { if (!first) sql.append(", "); sql.append("auto_group = ?"); args.add(autoGroup); first = false; }
        if (customHeader != null) { if (!first) sql.append(", "); sql.append("custom_header = ?"); args.add(customHeader); first = false; }
        if (paramOverride != null) { if (!first) sql.append(", "); sql.append("param_override = ?"); args.add(paramOverride); first = false; }
        if (channelProxy != null) { if (!first) sql.append(", "); sql.append("channel_proxy = ?"); args.add(channelProxy); first = false; }
        if (matchRegex != null) { if (!first) sql.append(", "); sql.append("match_regex = ?"); args.add(matchRegex); first = false; }
        if (first) return;
        sql.append(" WHERE id = ?");
        args.add(id);
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public void delete(int id) {
        jdbcTemplate.update("DELETE FROM channel_key WHERE channel_id = ?", id);
        jdbcTemplate.update("DELETE FROM channel WHERE id = ?", id);
    }

    public void setEnabled(int id, boolean enabled) {
        jdbcTemplate.update("UPDATE channel SET enabled = ? WHERE id = ?", enabled, id);
    }

    public boolean exists(int id) {
        var count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM channel WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    // ---- ChannelKey operations ----

    public List<ChannelKey> findKeysByChannelId(int channelId) {
        return jdbcTemplate.query(
            "SELECT id, channel_id, enabled, channel_key, status_code, last_use_time_stamp, total_cost, remark "
            + "FROM channel_key WHERE channel_id = ? ORDER BY id",
            (rs, rowNum) -> new ChannelKey(
                rs.getInt("id"),
                rs.getInt("channel_id"),
                rs.getBoolean("enabled"),
                rs.getString("channel_key"),
                rs.getInt("status_code"),
                rs.getLong("last_use_time_stamp"),
                rs.getDouble("total_cost"),
                rs.getString("remark")
            ),
            channelId
        );
    }

    public void addKey(int channelId, boolean enabled, String key, String remark) {
        jdbcTemplate.update(
            "INSERT INTO channel_key (channel_id, enabled, channel_key, status_code, last_use_time_stamp, total_cost, remark) "
            + "VALUES (?, ?, ?, 0, 0, 0, ?)",
            channelId, enabled, key, remark
        );
    }

    public void updateKey(int keyId, Boolean enabled, String channelKey, String remark) {
        var sql = new StringBuilder("UPDATE channel_key SET ");
        var args = new java.util.ArrayList<>();
        boolean first = true;
        if (enabled != null) { sql.append("enabled = ?"); args.add(enabled); first = false; }
        if (channelKey != null) { if (!first) sql.append(", "); sql.append("channel_key = ?"); args.add(channelKey); first = false; }
        if (remark != null) { if (!first) sql.append(", "); sql.append("remark = ?"); args.add(remark); first = false; }
        if (first) return;
        sql.append(" WHERE id = ?");
        args.add(keyId);
        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public void deleteKey(int keyId) {
        jdbcTemplate.update("DELETE FROM channel_key WHERE id = ?", keyId);
    }

    // ---- JSON parsing helpers ----

    private List<io.github.shigureyukina.octobus.domain.BaseUrl> parseBaseUrls(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var type = mapper.getTypeFactory().constructCollectionType(List.class, io.github.shigureyukina.octobus.domain.BaseUrl.class);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<io.github.shigureyukina.octobus.domain.CustomHeader> parseCustomHeaders(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var type = mapper.getTypeFactory().constructCollectionType(List.class, io.github.shigureyukina.octobus.domain.CustomHeader.class);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    public String serializeBaseUrls(java.util.List<io.github.shigureyukina.octobus.domain.BaseUrl> urls) {
        return urls == null ? "[]" : toJson(urls);
    }

    public void updateBaseUrls(int channelId, java.util.List<io.github.shigureyukina.octobus.domain.BaseUrl> baseUrls) {
        String json = serializeBaseUrls(baseUrls);
        jdbcTemplate.update("UPDATE channel SET base_urls = ? WHERE id = ?", json, channelId);
    }

    public String serializeCustomHeaders(java.util.List<io.github.shigureyukina.octobus.domain.CustomHeader> headers) {
        return headers == null ? "[]" : toJson(headers);
    }

    // ---- Backup/restore helpers ----

    /**
     * Inserts a channel row from backup with a specific ID.
     * Used by the incremental import (skip if ID already exists).
     */
    public void insertFromBackup(io.github.shigureyukina.octobus.domain.Channel ch) {
        jdbcTemplate.update(
            "INSERT INTO channel (id, name, type, enabled, base_urls, model, custom_model, proxy, "
            + "auto_sync, auto_group, custom_header, param_override, channel_proxy, match_regex) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ch.id(), ch.name(), ch.type(), ch.enabled() ? 1 : 0,
            serializeBaseUrls(ch.baseUrls()), ch.model(), ch.customModel(),
            ch.proxy() ? 1 : 0, ch.autoSync() ? 1 : 0, ch.autoGroup(),
            serializeCustomHeaders(ch.customHeader()), ch.paramOverride(),
            ch.channelProxy(), ch.matchRegex()
        );
    }

    /**
     * Inserts a channel_key row from backup with a specific ID.
     */
    public void insertKeyFromBackup(io.github.shigureyukina.octobus.domain.ChannelKey key) {
        jdbcTemplate.update(
            "INSERT INTO channel_key (id, channel_id, enabled, channel_key, status_code, "
            + "last_use_time_stamp, total_cost, remark) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            key.id(), key.channelId(), key.enabled() ? 1 : 0,
            key.channelKey(), key.statusCode(), key.lastUseTimeStamp(),
            key.totalCost(), key.remark()
        );
    }

    /**
     * Checks if a channel_key with the given ID exists.
     */
    public boolean existsKey(int keyId) {
        var count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM channel_key WHERE id = ?", Integer.class, keyId);
        return count != null && count > 0;
    }
}
