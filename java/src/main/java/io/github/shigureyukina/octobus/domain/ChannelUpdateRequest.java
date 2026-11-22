package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ChannelUpdateRequest(
    @JsonProperty(required = true) Integer id,
    String name,
    String type,
    Boolean enabled,
    @JsonProperty("base_urls") List<BaseUrl> baseUrls,
    String model,
    @JsonProperty("custom_model") String customModel,
    Boolean proxy,
    @JsonProperty("auto_sync") Boolean autoSync,
    @JsonProperty("auto_group") Integer autoGroup,
    @JsonProperty("custom_header") List<CustomHeader> customHeader,
    @JsonProperty("param_override") String paramOverride,
    @JsonProperty("channel_proxy") String channelProxy,
    @JsonProperty("match_regex") String matchRegex,
    @JsonProperty("keys_to_add") List<ChannelUpdateRequest.KeyToAdd> keysToAdd,
    @JsonProperty("keys_to_update") List<ChannelUpdateRequest.KeyToUpdate> keysToUpdate,
    @JsonProperty("keys_to_delete") List<Integer> keysToDelete
) {
    public record KeyToAdd(
        Boolean enabled,
        @JsonProperty("channel_key") String channelKey,
        String remark
    ) {
    }

    public record KeyToUpdate(
        Integer id,
        Boolean enabled,
        @JsonProperty("channel_key") String channelKey,
        String remark
    ) {
    }
}
