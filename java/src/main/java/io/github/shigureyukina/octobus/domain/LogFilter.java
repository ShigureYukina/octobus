package io.github.shigureyukina.octobus.domain;

/**
 * Query filter parameters for relay log listing.
 *
 * @param page        1-based page number (default 1)
 * @param pageSize    maximum rows per page (default 20, max 100)
 * @param startTime   unix seconds, optional lower bound
 * @param endTime     unix seconds, optional upper bound
 * @param modelName   optional request model name filter
 * @param channelName optional channel name filter
 * @param status      optional status filter: "error" or "success"
 */
public record LogFilter(
    int page,
    int pageSize,
    Long startTime,
    Long endTime,
    String modelName,
    String channelName,
    String status
) {

    /**
     * Zero-based offset for SQL LIMIT/OFFSET pagination.
     */
    public int offset() {
        return (page - 1) * pageSize;
    }
}
