package io.github.shigureyukina.octobus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Result of a database import operation, reporting rows affected per table.
 */
public record DBImportResult(
    @JsonProperty("rows_affected") Map<String, Long> rowsAffected
) {
}
