package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated response from the Gemini models endpoint.
 */
public record GeminiModelList(
    @JsonProperty("models") List<ModelEntry> models,
    @JsonProperty("nextPageToken") String nextPageToken
) {
    public record ModelEntry(
        @JsonProperty("name") String name
    ) {
    }
}
