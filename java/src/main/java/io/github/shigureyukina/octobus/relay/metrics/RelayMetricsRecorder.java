package io.github.shigureyukina.octobus.relay.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.domain.ChannelAttempt;
import io.github.shigureyukina.octobus.domain.RelayLog;
import io.github.shigureyukina.octobus.domain.StatsMetrics;
import io.github.shigureyukina.octobus.task.LogBuffer;
import io.github.shigureyukina.octobus.task.StatsAccumulator;
import io.github.shigureyukina.octobus.relay.metrics.UsageParser.ParsedUsage;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Records relay metrics (stats and audit log) for a completed relay request.
 *
 * <p>After a relay attempt succeeds or after all candidates are exhausted,
 * call one of the lifecycle methods to persist the seven statistics updates
 * and the {@link RelayLog} row.
 */
@Component
public class RelayMetricsRecorder {

    private final StatsAccumulator statsAccumulator;
    private final LogBuffer logBuffer;
    private final PriceLookup priceLookup;
    private final ObjectMapper objectMapper;

    public RelayMetricsRecorder(
        StatsAccumulator statsAccumulator,
        LogBuffer logBuffer,
        PriceLookup priceLookup,
        ObjectMapper objectMapper
    ) {
        this.statsAccumulator = statsAccumulator;
        this.logBuffer = logBuffer;
        this.priceLookup = priceLookup;
        this.objectMapper = objectMapper;
    }

    /**
     * Record metrics for a completed relay request, regardless of success or failure.
     *
     * @param success          whether the upstream returned a usable response
     * @param requestModel     the client-requested model name
     * @param actualModel      the actual upstream model used
     * @param apiKeyId         the API key that authenticated the request
     * @param channelId        the channel that served the request (0 on full exhaustion)
     * @param channelName      the channel name (empty on full exhaustion)
     * @param apiKeyName       the API key display name
     * @param usage            parsed token counts (zeroed on failure)
     * @param requestBody      the original request body JSON
     * @param responseBody     the upstream response body JSON (empty on failure)
     * @param error            error message (null on success)
     * @param attempts         the attempts chain from the iterator
     * @param startEpochSecond start time of the request (epoch seconds)
     * @param durationMs       total request duration in milliseconds
     * @param ftutMs           first-token-until time in ms (0 for non-streaming)
     */
    public void record(
        boolean success,
        String requestModel,
        String actualModel,
        int apiKeyId,
        int channelId,
        String channelName,
        String apiKeyName,
        ParsedUsage usage,
        String requestBody,
        String responseBody,
        String error,
        List<ChannelAttempt> attempts,
        long startEpochSecond,
        long durationMs,
        int ftutMs
    ) {
        // 1. Calculate cost
        ModelPrice price = priceLookup.priceFor(actualModel);
        var costResult = CostCalculator.calculate(usage, price);

        // 2. Build shared stats delta
        StatsMetrics statsDelta = new StatsMetrics(
            usage.promptTokens(),
            usage.completionTokens(),
            costResult.inputCost(),
            costResult.outputCost(),
            durationMs,
            success ? 1 : 0,
            success ? 0 : 1,
            usage.cacheReadTokens()
        );

        // 3. Record stats delta into memory buckets based on record time
        LocalDateTime recordedAt = LocalDateTime.now();
        statsAccumulator.record(recordedAt, statsDelta, apiKeyId, channelId);

        // 4. Save the relay log
        boolean hasUsage = success && hasUsageData(usage);

        int totalAttempts = attempts != null ? attempts.size() : 0;
        RelayLog log = new RelayLog(
            0L, // auto-generated id
            startEpochSecond,
            requestModel != null ? requestModel : "",
            apiKeyName != null ? apiKeyName : "",
            channelId,
            channelName != null ? channelName : "",
            actualModel != null ? actualModel : "",
            (int) usage.promptTokens(),
            (int) usage.completionTokens(),
            ftutMs,
            (int) durationMs,
            costResult.totalCost(),
            hasUsage,
            requestBody != null ? requestBody : "",
            responseBody != null ? responseBody : "",
            error != null ? error : "",
            attempts != null ? attempts : List.of(),
            totalAttempts,
            (int) usage.cacheReadTokens()
        );
        logBuffer.append(log);
    }

    /**
     * Convenience overload for a successful relay request.
     */
    public void recordSuccess(
        String requestModel,
        String actualModel,
        int apiKeyId,
        int channelId,
        String channelName,
        String apiKeyName,
        com.fasterxml.jackson.databind.JsonNode rawPayload,
        com.fasterxml.jackson.databind.JsonNode usageNode,
        String requestBody,
        List<ChannelAttempt> attempts,
        long startEpochSecond,
        long durationMs
    ) {
        ParsedUsage usage = parseUsageFromJson(usageNode);
        String responseBody = rawPayload != null ? toJsonString(rawPayload) : "";
        record(true, requestModel, actualModel, apiKeyId, channelId, channelName,
            apiKeyName, usage, requestBody, responseBody, null, attempts,
            startEpochSecond, durationMs, 0);
    }

    /**
     * Convenience overload for a failed relay request.
     */
    public void recordFailure(
        String requestModel,
        int apiKeyId,
        int channelId,
        String channelName,
        String apiKeyName,
        String error,
        String requestBody,
        List<ChannelAttempt> attempts,
        long startEpochSecond,
        long durationMs
    ) {
        ParsedUsage emptyUsage = new ParsedUsage(0, 0, 0, 0);
        record(false, requestModel, requestModel, apiKeyId, channelId, channelName,
            apiKeyName, emptyUsage, requestBody, "", error, attempts,
            startEpochSecond, durationMs, 0);
    }

    private ParsedUsage parseUsageFromJson(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new ParsedUsage(0, 0, 0, 0);
        }
        long prompt = node.path("prompt_tokens").asLong(0);
        long completion = node.path("completion_tokens").asLong(0);
        long cacheRead = 0;
        long cacheWrite = 0;
        var details = node.path("prompt_tokens_details");
        if (!details.isMissingNode() && !details.isNull()) {
            cacheRead = Math.max(0, details.path("cached_tokens").asLong(0));
            cacheWrite = Math.max(0, details.path("write_cached_tokens").asLong(0));
        }
        return new ParsedUsage(
            Math.max(0, prompt),
            Math.max(0, completion),
            cacheRead,
            cacheWrite
        );
    }

    private boolean hasUsageData(ParsedUsage usage) {
        return usage != null
            && (usage.promptTokens() > 0
            || usage.completionTokens() > 0
            || usage.cacheReadTokens() > 0
            || usage.cacheWriteTokens() > 0);
    }

    private String toJsonString(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return node.toString();
        }
    }
}
