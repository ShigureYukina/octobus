package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAIModelList(
    @JsonProperty("object") String object,
    @JsonProperty("data") List<OpenAIModel> data
) {
}
