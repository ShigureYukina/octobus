package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Channel(
    @JsonProperty("id") int id,
    @JsonProperty("name") String name,
    @JsonProperty("type") String type,
    @JsonProperty("enabled") Boolean enabled,
    @JsonProperty("base_urls") List<BaseUrl> baseUrls,
    List<ChannelKey> keys,
    String model,
    @JsonProperty("custom_model") String customModel,
    Boolean proxy,
    @JsonProperty("auto_sync") Boolean autoSync,
    @JsonProperty("auto_group") Integer autoGroup,
    @JsonProperty("custom_header") List<CustomHeader> customHeader,
    @JsonProperty("param_override") String paramOverride,
    @JsonProperty("channel_proxy") String channelProxy,
    @JsonProperty("match_regex") String matchRegex,
    @JsonProperty("stats") StatsChannel stats
) {
}
