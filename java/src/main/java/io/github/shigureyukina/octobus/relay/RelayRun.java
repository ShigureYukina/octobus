package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.APIKey;
import io.github.shigureyukina.octobus.domain.BaseUrl;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupItem;
import io.github.shigureyukina.octobus.relay.balancer.Balancer;
import io.github.shigureyukina.octobus.relay.provider.ProviderHttpClient;
import io.github.shigureyukina.octobus.relay.session.StickyStore;
import io.github.shigureyukina.octobus.relay.spi.InboundTransformer;
import io.github.shigureyukina.octobus.relay.spi.RelayProviderFormat;
import io.github.shigureyukina.octobus.relay.spi.TransformerRegistry;
import io.github.shigureyukina.octobus.relay.spi.UnifiedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Request-level relay state holder.
 *
 * <p>T1 carries the raw body, the inbound format, and an empty
 * candidate iterator. T2 adds the SPI registry and inbound transformer
 * reference without changing the route behavior yet. The unified request,
 * metrics recorder, resolved group, and the full candidate-walking loop are
 * filled in by later M3 sub-tasks.
 */
public class RelayRun {

    private final String rawBody;
    private final RelayInboundFormat inboundFormat;
    private final RelayCandidateIterator iterator;
    private final ServerRequest serverRequest;
    private final RelayRuntimeSupport runtimeSupport;
    private final TransformerRegistry transformerRegistry;
    private final InboundTransformer inboundTransformer;
    private final UnifiedRequest unifiedRequest;
    private final long startEpochSecond;

    public RelayRun(
        String rawBody,
        RelayInboundFormat inboundFormat,
        RelayCandidateIterator iterator
    ) {
        this(rawBody, inboundFormat, iterator, TransformerRegistry.defaultRegistry(), null);
    }

    public RelayRun(
        String rawBody,
        RelayInboundFormat inboundFormat,
        RelayCandidateIterator iterator,
        TransformerRegistry transformerRegistry,
        UnifiedRequest unifiedRequest
    ) {
        this.rawBody = rawBody == null ? "" : rawBody;
        this.inboundFormat = inboundFormat;
        this.iterator = iterator == null ? RelayCandidateIterator.empty() : iterator;
        this.transformerRegistry = transformerRegistry == null
            ? TransformerRegistry.defaultRegistry()
            : transformerRegistry;
        this.inboundTransformer = this.transformerRegistry.resolveInbound(inboundFormat);
        this.unifiedRequest = unifiedRequest;
        this.serverRequest = null;
        this.runtimeSupport = null;
        this.startEpochSecond = 0;
    }

    public RelayRun(
        ServerRequest serverRequest,
        String rawBody,
        RelayInboundFormat inboundFormat,
        RelayRuntimeSupport runtimeSupport
    ) {
        this.rawBody = rawBody == null ? "" : rawBody;
        this.inboundFormat = inboundFormat;
        this.iterator = RelayCandidateIterator.empty();
        this.serverRequest = serverRequest;
        this.runtimeSupport = runtimeSupport;
        this.transformerRegistry = runtimeSupport.transformerRegistry();
        this.inboundTransformer = this.transformerRegistry.resolveInbound(inboundFormat);
        this.unifiedRequest = null;
        this.startEpochSecond = java.time.Instant.now().getEpochSecond();
    }

    public String rawBody() {
        return rawBody;
    }

    public RelayInboundFormat inboundFormat() {
        return inboundFormat;
    }

    public RelayCandidateIterator iterator() {
        return iterator;
    }

    public TransformerRegistry transformerRegistry() {
        return transformerRegistry;
    }

    public InboundTransformer inboundTransformer() {
        return inboundTransformer;
    }

    public UnifiedRequest unifiedRequest() {
        return unifiedRequest;
    }

    public ServerRequest serverRequest() {
        return serverRequest;
    }

    public RelayRuntimeSupport runtimeSupport() {
        return runtimeSupport;
    }

    /**
     * Drive the relay pipeline for this request.
     *
     * <p>T1 always returns a controlled "adapter not wired" error so the
     * route is reachable but no real provider dispatch happens. The
     * candidate loop lands in T3, and the real attempt dispatch lands
     * in T6.
     */
    public Mono<ResponseEntity<?>> execute() {
        if (runtimeSupport != null && serverRequest != null) {
            return executeOpenAIChatPath();
        }
        String message = "Relay adapter is not yet wired for "
            + inboundFormat.name();
        return Mono.just(ResponseEntity
            .status(HttpStatus.NOT_IMPLEMENTED)
            .body(ApiResponse.error(HttpStatus.NOT_IMPLEMENTED, message)));
    }

    public Flux<ServerSentEvent<String>> executeStream() {
        if (runtimeSupport == null || serverRequest == null) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Streaming relay is not yet wired for " + inboundFormat.name());
        }
        if (inboundFormat != RelayInboundFormat.OPENAI_CHAT_COMPLETION) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Streaming is not yet supported for " + formatPath());
        }
        if (inboundTransformer == null) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Inbound format is not supported for " + inboundFormat.name());
        }

        UnifiedRequest request;
        try {
            request = inboundTransformer.transform(rawBody);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to parse relay request", exception);
        }

        APIKey apiKey = resolveApiKey();
        validateSupportedModels(apiKey, request.model());
        Group group = resolveGroup(request.model());
        CandidateBundle bundle = buildCandidates(group, apiKey, request.model());
        if (bundle.iterator().isEmpty()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No available channel");
        }

        RelayCandidate candidate;
        try {
            candidate = bundle.iterator().next();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "All channels failed", exception);
        }

        Channel channel = bundle.channelsById().get(candidate.channelId());
        ChannelKey channelKey = bundle.keysById().get(candidate.channelKeyId());
        if (channel == null || channelKey == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "candidate resources not found");
        }

        RelayProviderFormat providerFormat = RelayProviderFormat.fromChannelType(channel.type())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Channel type is not supported: " + channel.type()));
        var outboundTransformer = runtimeSupport.transformerRegistry().resolveOutbound(providerFormat);
        if (outboundTransformer == null || outboundTransformer.isGuarded()) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Outbound provider format is not yet supported for " + providerFormat.name());
        }

        UnifiedRequest providerRequest = new UnifiedRequest(
            request.requestType(),
            candidate.modelName(),
            request.stream(),
            request.messages(),
            request.tools(),
            request.options(),
            request.rawPayload()
        );
        JsonNode payload = outboundTransformer.serializeRequest(providerRequest);
        payload = applyParamOverride(channel, payload);
        ProviderHttpClient providerClient = switch (providerFormat) {
            case OPENAI_CHAT_COMPLETION -> runtimeSupport.openAICompatibleProviderClient();
            case ANTHROPIC_MESSAGE -> throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Streaming is not supported for " + providerFormat.name());
            case GEMINI_CONTENTS -> runtimeSupport.geminiProviderClient();
            default -> throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "Streaming is not supported for " + providerFormat.name());
        };
        Flux<ServerSentEvent<String>> stream = providerClient.executeStream(channel, channelKey, payload, outboundTransformer);

        return stream
            .doOnSubscribe(subscription -> bundle.iterator().recordAttempt(candidate.channelKeyId(), candidate.channelName(), "streaming", "forwarded"))
            .doOnComplete(() -> {
                runtimeSupport.circuitBreaker().recordSuccess(candidate.channelId(), candidate.channelKeyId(), candidate.modelName());
                Duration stickyTtl = Duration.ofSeconds(Math.max(group.sessionKeepTime(), 0));
                runtimeSupport.stickyStore().remember(apiKey.id(), request.model(), candidate.channelId(), candidate.channelKeyId(), stickyTtl);
                recordStreamSuccessMetrics(request.model(), candidate, apiKey, bundle.iterator().attempts());
            })
            .doOnError(error -> {
                runtimeSupport.circuitBreaker().recordFailure(candidate.channelId(), candidate.channelKeyId(), candidate.modelName());
                bundle.iterator().recordAttempt(candidate.channelKeyId(), candidate.channelName(), "failed", error.getMessage());
                recordFailureMetrics(request.model(), apiKey.id(), apiKey.name(), error.getMessage(), bundle.iterator().attempts());
            });
    }

    private Mono<ResponseEntity<?>> executeOpenAIChatPath() {
        if (inboundTransformer == null) {
            return error(HttpStatus.NOT_IMPLEMENTED, "Inbound format is not supported for " + inboundFormat.name());
        }

        final UnifiedRequest request;
        try {
            request = inboundTransformer.transform(rawBody);
        } catch (ApiException exception) {
            return error(exception.getStatus(), exception.getClientMessage());
        } catch (Exception exception) {
            return error(HttpStatus.BAD_REQUEST, "Failed to parse relay request");
        }

        final APIKey apiKey;
        try {
            apiKey = resolveApiKey();
            validateSupportedModels(apiKey, request.model());
        } catch (ApiException exception) {
            return error(exception.getStatus(), exception.getClientMessage());
        }

        final Group group;
        try {
            group = resolveGroup(request.model());
        } catch (ApiException exception) {
            return error(exception.getStatus(), exception.getClientMessage());
        }

        CandidateBundle bundle = buildCandidates(group, apiKey, request.model());
        if (bundle.iterator().isEmpty()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "No available channel");
        }

        return attemptCandidates(bundle, request, apiKey, apiKey.name(), group, null);
    }

    private Mono<ResponseEntity<?>> attemptCandidates(
        CandidateBundle bundle,
        UnifiedRequest request,
        APIKey apiKey,
        String apiKeyName,
        Group group,
        FailureContext lastFailure
    ) {
        if (!bundle.iterator().hasNext()) {
            recordFailureMetrics(request.model(), apiKey.id(), apiKeyName,
                lastFailure == null ? "All channels failed" : lastFailure.message(),
                bundle.iterator().attempts());
            return error(resolveFailureStatus(lastFailure), lastFailure == null ? "All channels failed" : lastFailure.message());
        }

        RelayCandidate candidate;
        try {
            candidate = bundle.iterator().next();
        } catch (Exception exception) {
            recordFailureMetrics(request.model(), apiKey.id(), apiKeyName,
                lastFailure == null ? "All channels failed" : lastFailure.message(),
                bundle.iterator().attempts());
            return error(resolveFailureStatus(lastFailure), lastFailure == null ? "All channels failed" : lastFailure.message());
        }

        Channel channel = bundle.channelsById().get(candidate.channelId());
        ChannelKey channelKey = bundle.keysById().get(candidate.channelKeyId());
        if (channel == null || channelKey == null) {
            bundle.iterator().recordAttempt(candidate.channelKeyId(), candidate.channelName(), "failed", "candidate resources not found");
            return attemptCandidates(bundle, request, apiKey, apiKeyName, group, new FailureContext("candidate resources not found", 0));
        }

        RelayAttempt attempt = new RelayAttempt(this, candidate, channel, channelKey, request, runtimeSupport);
        return attempt.forward().flatMap(result -> {
            if (result.success()) {
                runtimeSupport.circuitBreaker().recordSuccess(candidate.channelId(), candidate.channelKeyId(), candidate.modelName());
                Duration stickyTtl = Duration.ofSeconds(Math.max(group.sessionKeepTime(), 0));
                runtimeSupport.stickyStore().remember(apiKey.id(), request.model(), candidate.channelId(), candidate.channelKeyId(), stickyTtl);
                bundle.iterator().recordAttempt(candidate.channelKeyId(), candidate.channelName(), "success", "forwarded");

                recordSuccessMetrics(request.model(), candidate.modelName(),
                    apiKey.id(), apiKeyName, channel.id(), channel.name(),
                    result.body(), bundle.iterator().attempts());

                return Mono.just(ResponseEntity.status(result.statusCode()).body(result.body()));
            }

            runtimeSupport.circuitBreaker().recordFailure(candidate.channelId(), candidate.channelKeyId(), candidate.modelName());
            bundle.iterator().recordAttempt(candidate.channelKeyId(), candidate.channelName(), "failed", result.error());
            return attemptCandidates(bundle, request, apiKey, apiKeyName, group, new FailureContext(result.error(), result.statusCode()));
        });
    }

    private HttpStatus resolveFailureStatus(FailureContext failureContext) {
        if (failureContext == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        return switch (failureContext.statusCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private void recordSuccessMetrics(
        String requestModel,
        String actualModel,
        int apiKeyId,
        String apiKeyName,
        int channelId,
        String channelName,
        com.fasterxml.jackson.databind.JsonNode rawPayload,
        List<io.github.shigureyukina.octobus.domain.ChannelAttempt> attempts
    ) {
        if (runtimeSupport == null || startEpochSecond <= 0) {
            return;
        }
        long nowEpoch = java.time.Instant.now().getEpochSecond();
        long durationMs = (nowEpoch - startEpochSecond) * 1000;
        com.fasterxml.jackson.databind.JsonNode usageNode = rawPayload != null
            ? rawPayload.get("usage")
            : null;
        runtimeSupport.relayMetricsRecorder().recordSuccess(
            requestModel, actualModel, apiKeyId, channelId, channelName,
            apiKeyName, rawPayload, usageNode, rawBody,
            attempts, startEpochSecond, durationMs
        );
    }

    private void recordFailureMetrics(
        String requestModel,
        int apiKeyId,
        String apiKeyName,
        String error,
        List<io.github.shigureyukina.octobus.domain.ChannelAttempt> attempts
    ) {
        if (runtimeSupport == null || startEpochSecond <= 0) {
            return;
        }
        long nowEpoch = java.time.Instant.now().getEpochSecond();
        long durationMs = (nowEpoch - startEpochSecond) * 1000;
        runtimeSupport.relayMetricsRecorder().recordFailure(
            requestModel, apiKeyId, 0, "", apiKeyName,
            error, rawBody, attempts,
            startEpochSecond, durationMs
        );
    }

    private APIKey resolveApiKey() {
        String rawApiKey = serverRequest.attribute(io.github.shigureyukina.octobus.security.RequestAuthenticationFilter.API_KEY_ATTRIBUTE)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Missing API key"));

        APIKey apiKey = runtimeSupport.apiKeyRepository().findByApiKey(rawApiKey)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid API key"));

        if (!apiKey.enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "API key is disabled");
        }
        if (apiKey.expireAt() != null && apiKey.expireAt() > 0 && apiKey.expireAt() < Instant.now().getEpochSecond()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "API key has expired");
        }
        return apiKey;
    }

    private void validateSupportedModels(APIKey apiKey, String modelName) {
        if (apiKey.supportedModels() == null || apiKey.supportedModels().isBlank() || modelName == null || modelName.isBlank()) {
            return;
        }
        boolean supported = java.util.Arrays.stream(apiKey.supportedModels().split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .anyMatch(modelName::equals);
        if (!supported) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Model not supported");
        }
    }

    private Group resolveGroup(String modelName) {
        return runtimeSupport.groupRepository().findAll().stream()
            .filter(group -> matchesGroup(group, modelName))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Model not found"));
    }

    private boolean matchesGroup(Group group, String modelName) {
        if (group.matchRegex() == null || group.matchRegex().isBlank()) {
            return false;
        }
        return Pattern.compile(group.matchRegex()).matcher(modelName == null ? "" : modelName).matches();
    }

    private CandidateBundle buildCandidates(Group group, APIKey apiKey, String requestModel) {
        Balancer balancer = Balancer.forMode(group.mode());
        List<GroupItem> orderedItems = balancer.order(group.items());
        Map<Integer, Channel> channelsById = new HashMap<>();
        Map<Integer, ChannelKey> keysById = new HashMap<>();
        List<RelayCandidate> candidates = new ArrayList<>();

        for (GroupItem item : orderedItems) {
            Channel channel;
            try {
                channel = runtimeSupport.channelRepository().findById(item.channelId());
            } catch (Exception exception) {
                continue;
            }
            if (!Boolean.TRUE.equals(channel.enabled())) {
                continue;
            }
            if (!hasUsableBaseUrl(channel.baseUrls())) {
                continue;
            }

            ChannelKey channelKey = selectChannelKey(channel.keys());
            if (channelKey == null || channelKey.id() == null) {
                continue;
            }

            channelsById.put(channel.id(), channel);
            keysById.put(channelKey.id(), channelKey);
            candidates.add(new RelayCandidate(channel.id(), channelKey.id(), channel.name(), item.modelName()));
        }

        RelayCandidateIterator iterator = new RelayCandidateIterator(
            candidates,
            runtimeSupport.stickyStore(),
            runtimeSupport.circuitBreaker(),
            apiKey.id(),
            requestModel,
            Duration.ofSeconds(Math.max(group.sessionKeepTime(), 0))
        );
        return new CandidateBundle(iterator, Map.copyOf(channelsById), Map.copyOf(keysById));
    }

    private boolean hasUsableBaseUrl(List<BaseUrl> baseUrls) {
        return baseUrls != null && baseUrls.stream().anyMatch(baseUrl -> baseUrl != null && baseUrl.url() != null && !baseUrl.url().isBlank());
    }

    private ChannelKey selectChannelKey(List<ChannelKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        Instant now = Instant.now();
        return keys.stream()
            .filter(key -> key != null && Boolean.TRUE.equals(key.enabled()))
            .filter(key -> key.channelKey() != null && !key.channelKey().isBlank())
            .filter(key -> !isCoolingDownFromRateLimit(key, now))
            .min(Comparator.comparingDouble(key -> key.totalCost() == null ? 0.0 : key.totalCost()))
            .orElse(null);
    }

    private boolean isCoolingDownFromRateLimit(ChannelKey key, Instant now) {
        if (key.statusCode() == null || key.statusCode() != 429) {
            return false;
        }
        long lastUse = key.lastUseTimeStamp() == null ? 0 : key.lastUseTimeStamp();
        return lastUse > 0 && Instant.ofEpochSecond(lastUse).plus(Duration.ofMinutes(5)).isAfter(now);
    }

    private Mono<ResponseEntity<?>> error(HttpStatus status, String message) {
        return Mono.just(ResponseEntity.status(status).body(ApiResponse.error(status, message)));
    }

    private JsonNode applyParamOverride(Channel channel, JsonNode payload) {
        if (channel == null) return payload;
        String paramOverride = channel.paramOverride();
        if (paramOverride == null || paramOverride.isBlank()) return payload;
        if (!(payload instanceof ObjectNode payloadNode)) return payload;

        try {
            JsonNode override = runtimeSupport.objectMapper().readTree(paramOverride);
            if (override instanceof ObjectNode overrideNode) {
                overrideNode.fieldNames().forEachRemaining(field ->
                    payloadNode.set(field, overrideNode.get(field)));
            }
        } catch (Exception exception) {
            // Invalid JSON in param_override — skip merge
        }
        return payload;
    }

    private void recordStreamSuccessMetrics(
        String requestModel,
        RelayCandidate candidate,
        APIKey apiKey,
        List<io.github.shigureyukina.octobus.domain.ChannelAttempt> attempts
    ) {
        if (runtimeSupport == null || startEpochSecond <= 0) {
            return;
        }
        long nowEpoch = java.time.Instant.now().getEpochSecond();
        long durationMs = (nowEpoch - startEpochSecond) * 1000;
        runtimeSupport.relayMetricsRecorder().recordSuccess(
            requestModel,
            candidate.modelName(),
            apiKey.id(),
            candidate.channelId(),
            candidate.channelName(),
            apiKey.name(),
            null,
            null,
            rawBody,
            attempts,
            startEpochSecond,
            durationMs
        );
    }

    private String formatPath() {
        return inboundFormat.path();
    }

    private record CandidateBundle(
        RelayCandidateIterator iterator,
        Map<Integer, Channel> channelsById,
        Map<Integer, ChannelKey> keysById
    ) {
    }

    private record FailureContext(String message, int statusCode) {
    }
}
