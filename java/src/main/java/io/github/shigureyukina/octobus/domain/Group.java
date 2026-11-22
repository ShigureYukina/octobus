package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Group(
    @JsonProperty("id") int id,
    @JsonProperty("name") String name,
    @JsonProperty("mode") int mode,
    @JsonProperty("match_regex") String matchRegex,
    @JsonProperty("first_token_time_out") int firstTokenTimeOut,
    @JsonProperty("session_keep_time") int sessionKeepTime,
    @JsonProperty("items") List<GroupItem> items
) {
}
