package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record GroupCreateRequest(
    @NotBlank @JsonProperty("name") String name,
    @JsonProperty("mode") int mode,
    @JsonProperty("match_regex") String matchRegex,
    @JsonProperty("first_token_time_out") Integer firstTokenTimeOut,
    @JsonProperty("session_keep_time") Integer sessionKeepTime,
    @JsonProperty("items") List<CreateItem> items
) {
    public record CreateItem(
        @JsonProperty("channel_id") int channelId,
        @JsonProperty("model_name") String modelName,
        @JsonProperty("priority") Integer priority,
        @JsonProperty("weight") Integer weight
    ) {}
}
