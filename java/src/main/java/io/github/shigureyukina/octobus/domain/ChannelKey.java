package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChannelKey(
    Integer id,
    @JsonProperty("channel_id") Integer channelId,
    Boolean enabled,
    @JsonProperty("channel_key") String channelKey,
    @JsonProperty("status_code") Integer statusCode,
    @JsonProperty("last_use_time_stamp") Long lastUseTimeStamp,
    @JsonProperty("total_cost") Double totalCost,
    String remark
) {
}
