package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Paginated relay log list response matching the Go API shape.
 */
public record LogListResponse(
    @JsonProperty("items") List<RelayLog> items,
    @JsonProperty("total") long total,
    @JsonProperty("page") int page,
    @JsonProperty("page_size") int pageSize
) {
}
