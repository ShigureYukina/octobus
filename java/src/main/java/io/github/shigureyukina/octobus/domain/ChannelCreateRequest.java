package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ChannelCreateRequest(
    @NotBlank String name,
    String type,
    Boolean enabled,
    @JsonProperty("base_urls") List<BaseUrl> baseUrls,
    List<ChannelKeyAdd> keys,
    String model,
    @JsonProperty("custom_model") String customModel,
    Boolean proxy,
    @JsonProperty("auto_sync") Boolean autoSync,
    @JsonProperty("auto_group") Integer autoGroup,
    @JsonProperty("custom_header") List<CustomHeader> customHeader,
    @JsonProperty("param_override") String paramOverride,
    @JsonProperty("channel_proxy") String channelProxy,
    @JsonProperty("match_regex") String matchRegex
) {
    public record ChannelKeyAdd(
        Boolean enabled,
        @NotBlank @JsonProperty("channel_key") String channelKey,
        String remark
    ) {
    }
}
