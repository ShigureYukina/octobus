package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.config.DatabaseProperties;
import io.github.shigureyukina.octobus.persistence.APIKeyRepository;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import io.github.shigureyukina.octobus.persistence.LogRepository;
import io.github.shigureyukina.octobus.persistence.ModelRepository;
import io.github.shigureyukina.octobus.persistence.SettingRepository;
import io.github.shigureyukina.octobus.persistence.StatsRepository;
import io.github.shigureyukina.octobus.persistence.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dedicated backup/restore service for full-database export and import.
 * Separated from SettingService to avoid bloating the setting-only logic.
 */
@Service
public class BackupService {

    private static final int DUMP_VERSION = 1;

    private final ModelRepository modelRepository;
    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final APIKeyRepository apiKeyRepository;
    private final SettingRepository settingRepository;
    private final UserRepository userRepository;
    private final LogRepository logRepository;
    private final StatsRepository statsRepository;
    private final TransactionTemplate transactionTemplate;
    private final DatabaseProperties databaseProperties;
    private final ObjectMapper objectMapper;

    public BackupService(
        ModelRepository modelRepository,
        ChannelRepository channelRepository,
        GroupRepository groupRepository,
        APIKeyRepository apiKeyRepository,
        SettingRepository settingRepository,
        UserRepository userRepository,
        LogRepository logRepository,
        StatsRepository statsRepository,
        TransactionTemplate transactionTemplate,
        DatabaseProperties databaseProperties,
        ObjectMapper objectMapper
    ) {
        this.modelRepository = modelRepository;
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.settingRepository = settingRepository;
        this.userRepository = userRepository;
        this.logRepository = logRepository;
        this.statsRepository = statsRepository;
        this.transactionTemplate = transactionTemplate;
        this.databaseProperties = databaseProperties;
        this.objectMapper = objectMapper;
    }

    // ========== Export ==========

    /**
     * Exports all core tables and optionally logs/stats.
     */
    public DBDump exportAll(boolean includeLogs, boolean includeStats) {
        // Core tables
        var channels = channelRepository.findAll();
        var channelKeys = channels.stream()
            .flatMap(ch -> ch.keys().stream())
            .toList();
        var groups = groupRepository.findAll();
        var groupItems = groups.stream()
            .flatMap(g -> g.items().stream())
            .toList();
        var llmInfos = modelRepository.findAll();
        var apiKeys = apiKeyRepository.findAll();
        var settings = settingRepository.findAll();

        if (!includeLogs && !includeStats) {
            return DBDump.core(channels, channelKeys, groups, groupItems,
                llmInfos, apiKeys, settings);
        }

        // Optional tables
        List<RelayLog> relayLogs = includeLogs ? logRepository.findAllForExport() : List.of();
        List<StatsTotal> statsTotal = includeStats ? List.of(statsRepository.findTotal()) : List.of();
        List<StatsDaily> statsDaily = includeStats ? statsRepository.findAllDaily() : List.of();
        List<StatsHourly> statsHourly = includeStats ? statsRepository.findAllHourly() : List.of();
        List<StatsChannel> statsChannel = includeStats ? statsRepository.findAllChannel() : List.of();
        List<StatsAPIKey> statsApiKey = includeStats ? statsRepository.findByApiKey() : List.of();

        return DBDump.withOptions(
            channels, channelKeys, groups, groupItems, llmInfos, apiKeys, settings,
            includeLogs, includeStats,
            relayLogs, statsTotal, statsDaily, statsHourly, statsChannel, statsApiKey
        );
    }

    // ========== Import ==========

    /**
     * Imports a full database dump within a single transaction.
     * On failure the transaction rolls back and the database remains unchanged.
     *
     * @param dump the parsed dump object
     * @return import result with rows affected per table
     */
    public DBImportResult importDump(DBDump dump) {
        if (dump == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "empty dump");
        }
        if (dump.version() != 0 && dump.version() != DUMP_VERSION) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "unsupported dump version: " + dump.version());
        }

        return transactionTemplate.execute(status -> {
            Map<String, Long> rowsAffected = new LinkedHashMap<>();

            try {
                // Import order follows FK/dependency constraints:
                // channel -> channel_key, groups -> group_items
                // model and setting are independent
                // api_key is independent

                // 1. Channels (skip existing by ID)
                if (dump.channels() != null && !dump.channels().isEmpty()) {
                    long n = importChannels(dump.channels());
                    rowsAffected.put("channels", n);
                }

                // 2. Channel keys (skip existing by ID)
                if (dump.channelKeys() != null && !dump.channelKeys().isEmpty()) {
                    long n = importChannelKeys(dump.channelKeys());
                    rowsAffected.put("channel_keys", n);
                }

                // 3. Groups (skip existing by ID)
                if (dump.groups() != null && !dump.groups().isEmpty()) {
                    long n = importGroups(dump.groups());
                    rowsAffected.put("groups", n);
                }

                // 4. Group items (skip existing by ID)
                if (dump.groupItems() != null && !dump.groupItems().isEmpty()) {
                    long n = importGroupItems(dump.groupItems());
                    rowsAffected.put("group_items", n);
                }

                // 5. LLM infos / model (upsert by name)
                if (dump.llmInfos() != null && !dump.llmInfos().isEmpty()) {
                    long n = importModels(dump.llmInfos());
                    rowsAffected.put("llm_infos", n);
                }

                // 6. API keys (skip existing by ID)
                if (dump.apiKeys() != null && !dump.apiKeys().isEmpty()) {
                    long n = importApiKeys(dump.apiKeys());
                    rowsAffected.put("api_keys", n);
                }

                // 7. Settings (upsert by key)
                if (dump.settings() != null && !dump.settings().isEmpty()) {
                    long n = importSettings(dump.settings());
                    rowsAffected.put("settings", n);
                }

                // 8. Optional stats
                if (dump.includeStats()) {
                    if (dump.statsTotal() != null && !dump.statsTotal().isEmpty()) {
                        long n = importStatsTotal(dump.statsTotal());
                        rowsAffected.put("stats_total", n);
                    }
                    if (dump.statsDaily() != null && !dump.statsDaily().isEmpty()) {
                        long n = importStatsDaily(dump.statsDaily());
                        rowsAffected.put("stats_daily", n);
                    }
                    if (dump.statsHourly() != null && !dump.statsHourly().isEmpty()) {
                        long n = importStatsHourly(dump.statsHourly());
                        rowsAffected.put("stats_hourly", n);
                    }
                    if (dump.statsChannel() != null && !dump.statsChannel().isEmpty()) {
                        long n = importStatsChannel(dump.statsChannel());
                        rowsAffected.put("stats_channel", n);
                    }
                    if (dump.statsApiKey() != null && !dump.statsApiKey().isEmpty()) {
                        long n = importStatsApiKey(dump.statsApiKey());
                        rowsAffected.put("stats_api_key", n);
                    }
                }

                // 9. Optional logs
                if (dump.includeLogs() && dump.relayLogs() != null && !dump.relayLogs().isEmpty()) {
                    logRepository.saveAll(dump.relayLogs());
                    rowsAffected.put("relay_logs", (long) dump.relayLogs().size());
                }

            } catch (Exception e) {
                status.setRollbackOnly();
                if (e instanceof ApiException apiEx) {
                    throw apiEx;
                }
                throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Import failed: " + e.getMessage(), e);
            }

            return new DBImportResult(rowsAffected);
        });
    }

    /**
     * Parses a DBDump from raw bytes, supporting both plain dump JSON
     * and wrapped ApiResponse format ({code,message,data}).
     */
    public DBDump parseDump(byte[] content) {
        try {
            DBDump dump = objectMapper.readValue(content, DBDump.class);
            // If the dump looks empty, try unwrapping from ApiResponse
            if (dump != null && isDumpEmpty(dump)) {
                var wrapper = objectMapper.readValue(content,
                    new TypeReference<ApiResponse<DBDump>>() {});
                if (wrapper != null && wrapper.data() != null) {
                    return wrapper.data();
                }
            }
            return dump;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                "Invalid dump format: " + e.getMessage());
        }
    }

    private boolean isDumpEmpty(DBDump dump) {
        return dump.version() == 0
            && (dump.channels() == null || dump.channels().isEmpty())
            && (dump.channelKeys() == null || dump.channelKeys().isEmpty())
            && (dump.groups() == null || dump.groups().isEmpty())
            && (dump.groupItems() == null || dump.groupItems().isEmpty())
            && (dump.settings() == null || dump.settings().isEmpty())
            && (dump.apiKeys() == null || dump.apiKeys().isEmpty())
            && (dump.llmInfos() == null || dump.llmInfos().isEmpty())
            && (dump.relayLogs() == null || dump.relayLogs().isEmpty())
            && (dump.statsDaily() == null || dump.statsDaily().isEmpty())
            && (dump.statsHourly() == null || dump.statsHourly().isEmpty())
            && (dump.statsTotal() == null || dump.statsTotal().isEmpty())
            && (dump.statsChannel() == null || dump.statsChannel().isEmpty())
            && (dump.statsApiKey() == null || dump.statsApiKey().isEmpty());
    }

    // ========== Table-level import helpers ==========

    private long importChannels(List<Channel> channels) {
        long count = 0;
        for (var ch : channels) {
            if (ch.id() <= 0) continue;
            if (!channelRepository.exists(ch.id())) {
                channelRepository.insertFromBackup(ch);
                count++;
            }
        }
        return count;
    }

    private long importChannelKeys(List<ChannelKey> keys) {
        long count = 0;
        for (var key : keys) {
            if (key.id() == null) continue;
            if (!channelRepository.existsKey(key.id())) {
                channelRepository.insertKeyFromBackup(key);
                count++;
            }
        }
        return count;
    }

    private long importGroups(List<Group> groups) {
        long count = 0;
        for (var g : groups) {
            if (!groupRepository.existsById(g.id())) {
                groupRepository.insertFromBackup(g);
                count++;
            }
        }
        return count;
    }

    private long importGroupItems(List<GroupItem> items) {
        long count = 0;
        List<GroupItem> toInsert = new ArrayList<>();
        for (var item : items) {
            if (!groupRepository.existsItemById(item.id())) {
                toInsert.add(item);
            }
        }
        if (!toInsert.isEmpty()) {
            groupRepository.batchInsertItemsFromBackup(toInsert);
            count = toInsert.size();
        }
        return count;
    }

    private long importModels(List<ModelInfo> models) {
        long count = 0;
        for (var m : models) {
            modelRepository.upsert(m.name(), m.input(), m.output(),
                m.cacheRead(), m.cacheWrite());
            count++;
        }
        return count;
    }

    private long importApiKeys(List<APIKey> keys) {
        long count = 0;
        for (var key : keys) {
            if (!apiKeyRepository.existsById(key.id())) {
                apiKeyRepository.insertFromBackup(key);
                count++;
            }
        }
        return count;
    }

    private long importSettings(List<Setting> settings) {
        long count = 0;
        for (var s : settings) {
            settingRepository.upsert(s.key(), s.value());
            count++;
        }
        return count;
    }

    private long importStatsTotal(List<StatsTotal> rows) {
        for (var row : rows) {
            statsRepository.replaceTotal(row);
        }
        return rows.size();
    }

    private long importStatsDaily(List<StatsDaily> rows) {
        for (var row : rows) {
            statsRepository.replaceDaily(row);
        }
        return rows.size();
    }

    private long importStatsHourly(List<StatsHourly> rows) {
        for (var row : rows) {
            statsRepository.replaceHourly(row);
        }
        return rows.size();
    }

    private long importStatsChannel(List<StatsChannel> rows) {
        for (var row : rows) {
            statsRepository.replaceChannel(row);
        }
        return rows.size();
    }

    private long importStatsApiKey(List<StatsAPIKey> rows) {
        for (var row : rows) {
            statsRepository.replaceApiKey(row);
        }
        return rows.size();
    }
}
