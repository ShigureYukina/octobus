package io.github.shigureyukina.octobus.persistence;

import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupIDAndLLMName;
import io.github.shigureyukina.octobus.domain.GroupItem;
import io.github.shigureyukina.octobus.domain.GroupCreateRequest;
import io.github.shigureyukina.octobus.domain.GroupUpdateRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class GroupRepository {

    private static final RowMapper<Group> GROUP_MAPPER = (rs, rowNum) -> new Group(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getInt("mode"),
        rs.getString("match_regex"),
        rs.getInt("first_token_time_out"),
        rs.getInt("session_keep_time"),
        null
    );

    private static final RowMapper<GroupItem> ITEM_MAPPER = (rs, rowNum) -> new GroupItem(
        rs.getInt("id"),
        rs.getInt("group_id"),
        rs.getInt("channel_id"),
        rs.getString("model_name"),
        rs.getInt("priority"),
        rs.getInt("weight")
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public GroupRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public List<Group> findAll() {
        List<Group> groups = jdbcTemplate.query(
            "SELECT id, name, mode, match_regex, first_token_time_out, session_keep_time FROM `groups` ORDER BY id",
            GROUP_MAPPER
        );
        if (groups.isEmpty()) return groups;

        Map<Integer, List<GroupItem>> itemsByGroup = new HashMap<>();
        jdbcTemplate.query(
            "SELECT id, group_id, channel_id, model_name, priority, weight FROM group_items ORDER BY group_id, priority",
            ITEM_MAPPER
        ).forEach(item -> itemsByGroup.computeIfAbsent(item.groupId(), k -> new ArrayList<>()).add(item));

        return groups.stream()
            .map(g -> new Group(g.id(), g.name(), g.mode(), g.matchRegex(),
                g.firstTokenTimeOut(), g.sessionKeepTime(),
                itemsByGroup.getOrDefault(g.id(), List.of())))
            .toList();
    }

    public Optional<Group> findById(int id) {
        List<Group> groups = jdbcTemplate.query(
            "SELECT id, name, mode, match_regex, first_token_time_out, session_keep_time FROM `groups` WHERE id = ?",
            GROUP_MAPPER, id
        );
        if (groups.isEmpty()) return Optional.empty();

        Group g = groups.getFirst();
        List<GroupItem> items = jdbcTemplate.query(
            "SELECT id, group_id, channel_id, model_name, priority, weight FROM group_items WHERE group_id = ? ORDER BY priority",
            ITEM_MAPPER, id
        );
        return Optional.of(new Group(g.id(), g.name(), g.mode(), g.matchRegex(),
            g.firstTokenTimeOut(), g.sessionKeepTime(), items));
    }

    public Group create(GroupCreateRequest request) {
        return transactionTemplate.execute(status -> {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO `groups` (name, mode, match_regex, first_token_time_out, session_keep_time) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, request.name());
                ps.setInt(2, request.mode());
                ps.setString(3, request.matchRegex());
                ps.setInt(4, request.firstTokenTimeOut() != null ? request.firstTokenTimeOut() : 0);
                ps.setInt(5, request.sessionKeepTime() != null ? request.sessionKeepTime() : 0);
                return ps;
            }, keyHolder);

            int groupId = Objects.requireNonNull(keyHolder.getKey()).intValue();

            if (request.items() != null && !request.items().isEmpty()) {
                batchInsertItems(groupId, request.items().stream()
                    .map(item -> new Object[]{
                        item.channelId(),
                        item.modelName(),
                        item.priority() != null ? item.priority() : 0,
                        item.weight() != null ? item.weight() : 0
                    }).toList());
            }

            return findById(groupId).orElseThrow(() -> new IllegalStateException("Failed to retrieve created group"));
        });
    }

    public Group update(int id, GroupUpdateRequest request) {
        return transactionTemplate.execute(status -> {
            if (request.name() != null)
                jdbcTemplate.update("UPDATE `groups` SET name = ? WHERE id = ?", request.name(), id);
            if (request.mode() != null)
                jdbcTemplate.update("UPDATE `groups` SET mode = ? WHERE id = ?", request.mode(), id);
            if (request.matchRegex() != null)
                jdbcTemplate.update("UPDATE `groups` SET match_regex = ? WHERE id = ?", request.matchRegex(), id);
            if (request.firstTokenTimeOut() != null)
                jdbcTemplate.update("UPDATE `groups` SET first_token_time_out = ? WHERE id = ?", request.firstTokenTimeOut(), id);
            if (request.sessionKeepTime() != null)
                jdbcTemplate.update("UPDATE `groups` SET session_keep_time = ? WHERE id = ?", request.sessionKeepTime(), id);

            if (request.itemsToDelete() != null && !request.itemsToDelete().isEmpty()) {
                deleteItemsByIds(request.itemsToDelete());
            }

            if (request.itemsToUpdate() != null && !request.itemsToUpdate().isEmpty()) {
                for (var item : request.itemsToUpdate()) {
                    jdbcTemplate.update(
                        "UPDATE group_items SET priority = ?, weight = ? WHERE id = ?",
                        item.priority() != null ? item.priority() : 0,
                        item.weight() != null ? item.weight() : 0,
                        item.id()
                    );
                }
            }

            if (request.itemsToAdd() != null && !request.itemsToAdd().isEmpty()) {
                batchInsertItems(id, request.itemsToAdd().stream()
                    .map(item -> new Object[]{
                        item.channelId(),
                        item.modelName(),
                        item.priority() != null ? item.priority() : 0,
                        item.weight() != null ? item.weight() : 0
                    }).toList());
            }

            return findById(id).orElseThrow(() -> new IllegalStateException("Group not found after update"));
        });
    }

    public void delete(int id) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM group_items WHERE group_id = ?", id);
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", id);
        });
    }

    /**
     * Deletes all group_items for a given channel that match one of the specified model names.
     */
    public void deleteItemsByChannelAndModels(int channelId, List<String> modelNames) {
        if (modelNames == null || modelNames.isEmpty()) return;
        var placeholders = String.join(",", modelNames.stream().map(m -> "?").toList());
        jdbcTemplate.update(
            "DELETE FROM group_items WHERE channel_id = ? AND model_name IN (" + placeholders + ")",
            Stream.concat(Stream.of(channelId), modelNames.stream()).toArray()
        );
    }

    /**
     * Deletes all group_items for a given channel (across all groups).
     */
    public void deleteItemsByChannel(int channelId) {
        jdbcTemplate.update("DELETE FROM group_items WHERE channel_id = ?", channelId);
    }

    /**
     * Batch-inserts group_items for the given group with default priority=0 and weight=0.
     */
    public void batchAddItems(int groupId, List<GroupIDAndLLMName> items) {
        if (items == null || items.isEmpty()) return;
        batchInsertItems(groupId, items.stream()
            .map(item -> new Object[]{item.channelId(), item.modelName(), 0, 0})
            .toList());
    }

    private void batchInsertItems(int groupId, List<Object[]> items) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO group_items (group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?)",
            items.stream().map(args -> new Object[]{
                groupId, args[0], args[1], args[2], args[3]
            }).toList()
        );
    }

    private void deleteItemsByIds(List<Integer> ids) {
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        jdbcTemplate.update(
            "DELETE FROM group_items WHERE id IN (" + placeholders + ")",
            ids.toArray()
        );
    }

    // ---- Backup/restore helpers ----

    /**
     * Checks if a group with the given ID exists.
     */
    public boolean existsById(int id) {
        var count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM `groups` WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * Checks if a group_item with the given ID exists.
     */
    public boolean existsItemById(int id) {
        var count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM group_items WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    /**
     * Inserts a group row from backup with a specific ID.
     */
    public void insertFromBackup(Group group) {
        jdbcTemplate.update(
            "INSERT INTO `groups` (id, name, mode, match_regex, first_token_time_out, session_keep_time) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
            group.id(), group.name(), group.mode(), group.matchRegex(),
            group.firstTokenTimeOut(), group.sessionKeepTime()
        );
    }

    /**
     * Batch-inserts group_items from backup preserving their original IDs.
     */
    public void batchInsertItemsFromBackup(List<GroupItem> items) {
        if (items == null || items.isEmpty()) return;
        jdbcTemplate.batchUpdate(
            "INSERT INTO group_items (id, group_id, channel_id, model_name, priority, weight) VALUES (?, ?, ?, ?, ?, ?)",
            items, items.size(), (ps, item) -> {
                ps.setInt(1, item.id());
                ps.setInt(2, item.groupId());
                ps.setInt(3, item.channelId());
                ps.setString(4, item.modelName());
                ps.setInt(5, item.priority());
                ps.setInt(6, item.weight());
            }
        );
    }
}
