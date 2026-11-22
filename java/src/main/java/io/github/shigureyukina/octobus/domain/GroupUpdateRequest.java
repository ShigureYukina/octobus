package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GroupUpdateRequest(
    @JsonProperty("id") int id,
    @JsonProperty("name") String name,
    @JsonProperty("mode") Integer mode,
    @JsonProperty("match_regex") String matchRegex,
    @JsonProperty("first_token_time_out") Integer firstTokenTimeOut,
    @JsonProperty("session_keep_time") Integer sessionKeepTime,
    @JsonProperty("items_to_add") List<ItemAdd> itemsToAdd,
    @JsonProperty("items_to_update") List<ItemUpdate> itemsToUpdate,
    @JsonProperty("items_to_delete") List<Integer> itemsToDelete
) {
    public record ItemAdd(
        @JsonProperty("channel_id") int channelId,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("weight") Integer weight
    ) {}

    public record ItemUpdate(
        @JsonProperty("id") int id,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("weight") Integer weight
    ) {}
}
