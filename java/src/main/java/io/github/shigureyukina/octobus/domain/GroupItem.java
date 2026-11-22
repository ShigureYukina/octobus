package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroupItem(
    @JsonProperty("id") int id,
    @JsonProperty("group_id") int groupId,
    @JsonProperty("channel_id") int channelId,
    @JsonProperty("model_name") String modelName,
    @JsonProperty("priority") int priority,
    @JsonProperty("weight") int weight
) {
}
