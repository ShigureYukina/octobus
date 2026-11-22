package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Full-database JSON export format compatible with the Go DBDump struct.
 * Import uses incremental semantics (insert new rows, upsert on key-based tables).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DBDump(
    @JsonProperty("version") int version,
    @JsonProperty("exported_at") Instant exportedAt,
    @JsonProperty("include_logs") boolean includeLogs,
    @JsonProperty("include_stats") boolean includeStats,

    // Core tables
    @JsonProperty("channels") List<Channel> channels,
    @JsonProperty("channel_keys") List<ChannelKey> channelKeys,
    @JsonProperty("groups") List<Group> groups,
    @JsonProperty("group_items") List<GroupItem> groupItems,
    @JsonProperty("llm_infos") List<ModelInfo> llmInfos,
    @JsonProperty("api_keys") List<APIKey> apiKeys,
    @JsonProperty("settings") List<Setting> settings,

    // Optional stats tables
    @JsonProperty("stats_total") List<StatsTotal> statsTotal,
    @JsonProperty("stats_daily") List<StatsDaily> statsDaily,
    @JsonProperty("stats_hourly") List<StatsHourly> statsHourly,
    @JsonProperty("stats_channel") List<StatsChannel> statsChannel,
    @JsonProperty("stats_api_key") List<StatsAPIKey> statsApiKey,

    // Optional logs
    @JsonProperty("relay_logs") List<RelayLog> relayLogs
) {
    public static final int CURRENT_VERSION = 1;

    /**
     * Creates a core-only dump (no logs, no stats).
     */
    public static DBDump core(
        List<Channel> channels,
        List<ChannelKey> channelKeys,
        List<Group> groups,
        List<GroupItem> groupItems,
        List<ModelInfo> llmInfos,
        List<APIKey> apiKeys,
        List<Setting> settings
    ) {
        return new DBDump(
            CURRENT_VERSION, Instant.now(), false, false,
            channels, channelKeys, groups, groupItems, llmInfos, apiKeys, settings,
            null, null, null, null, null, null
        );
    }

    /**
     * Creates a dump with optional logs and/or stats.
     */
    public static DBDump withOptions(
        List<Channel> channels,
        List<ChannelKey> channelKeys,
        List<Group> groups,
        List<GroupItem> groupItems,
        List<ModelInfo> llmInfos,
        List<APIKey> apiKeys,
        List<Setting> settings,
        boolean includeLogs,
        boolean includeStats,
        List<RelayLog> relayLogs,
        List<StatsTotal> statsTotal,
        List<StatsDaily> statsDaily,
        List<StatsHourly> statsHourly,
        List<StatsChannel> statsChannel,
        List<StatsAPIKey> statsApiKey
    ) {
        return new DBDump(
            CURRENT_VERSION, Instant.now(), includeLogs, includeStats,
            channels, channelKeys, groups, groupItems, llmInfos, apiKeys, settings,
            includeStats ? statsTotal : null,
            includeStats ? statsDaily : null,
            includeStats ? statsHourly : null,
            includeStats ? statsChannel : null,
            includeStats ? statsApiKey : null,
            includeLogs ? relayLogs : null
        );
    }
}
