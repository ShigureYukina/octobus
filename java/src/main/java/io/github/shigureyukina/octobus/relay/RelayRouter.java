package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.APIKey;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupItem;
import io.github.shigureyukina.octobus.domain.ModelService;
import io.github.shigureyukina.octobus.domain.OpenAIModel;
import io.github.shigureyukina.octobus.domain.OpenAIModelList;
import io.github.shigureyukina.octobus.security.RequestAuthenticationFilter;
import static io.github.shigureyukina.octobus.relay.RelayInboundFormat.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Registers relay routes under {@code /v1/*} behind the
 * {@link io.github.shigureyukina.octobus.security.RequestAuthenticationFilter}.
 */
@Configuration
public class RelayRouter {

    @Bean
    public RouterFunction<ServerResponse> relayRoutes(
        ObjectMapper objectMapper,
        ObjectProvider<RelayRuntimeSupport> runtimeSupportProvider,
        ObjectProvider<ModelService> modelServiceProvider
    ) {
        RelayRuntimeSupport runtimeSupport = runtimeSupportProvider.getIfAvailable();
        ModelService modelService = modelServiceProvider.getIfAvailable();

        Map<RelayInboundFormat, HandlerFunction<ServerResponse>> handlers = new EnumMap<>(RelayInboundFormat.class);
        for (RelayInboundFormat format : RelayInboundFormat.values()) {
            handlers.put(format, handlerFor(format, objectMapper, runtimeSupport));
        }

        RouterFunctions.Builder builder = RouterFunctions.route();
        if (modelService != null) {
            builder = builder.GET("/v1/models", request -> listModels(request, modelService, runtimeSupport));
        }
        for (Map.Entry<RelayInboundFormat, HandlerFunction<ServerResponse>> entry : handlers.entrySet()) {
            builder = builder.POST(entry.getKey().path(), entry.getValue());
        }
        return builder.build();
    }

    private static HandlerFunction<ServerResponse> handlerFor(
        RelayInboundFormat format,
        ObjectMapper objectMapper,
        RelayRuntimeSupport runtimeSupport
    ) {
        RelayHandler handler = runtimeSupport == null
            ? new RelayHandler(format, objectMapper)
            : new RelayHandler(format, runtimeSupport, objectMapper);
        return handler::handle;
    }

    private static ServerResponse.BodyBuilder jsonOk() {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON);
    }

    private static reactor.core.publisher.Mono<ServerResponse> listModels(
        ServerRequest request,
        ModelService modelService,
        RelayRuntimeSupport runtimeSupport
    ) {
        APIKey apiKey = resolveApiKey(request, runtimeSupport);
        List<String> modelNames = listPublicModelNames(modelService, runtimeSupport, apiKey);
        Set<String> supportedModels = parseSupportedModels(apiKey);
        long created = Instant.now().getEpochSecond();

        List<OpenAIModel> data = modelNames.stream()
            .filter(modelName -> supportedModels == null || supportedModels.contains(modelName))
            .map(modelName -> new OpenAIModel(modelName, "model", created, "octopus"))
            .toList();

        return jsonOk().bodyValue(new OpenAIModelList("list", data));
    }

    private static APIKey resolveApiKey(ServerRequest request, RelayRuntimeSupport runtimeSupport) {
        if (runtimeSupport == null) {
            return null;
        }

        String rawApiKey = request.attribute(RequestAuthenticationFilter.API_KEY_ATTRIBUTE)
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

    private static List<String> listPublicModelNames(
        ModelService modelService,
        RelayRuntimeSupport runtimeSupport,
        APIKey apiKey
    ) {
        if (runtimeSupport == null || apiKey == null) {
            return modelService.listModelNames();
        }

        Map<Integer, Channel> channelsById = runtimeSupport.channelRepository().findAll().stream()
            .filter(channel -> channel != null && Boolean.TRUE.equals(channel.enabled()))
            .filter(channel -> hasUsableBaseUrl(channel))
            .filter(channel -> hasUsableChannelKey(channel.keys()))
            .collect(Collectors.toMap(Channel::id, channel -> channel, (left, right) -> left, HashMap::new));

        return modelService.listModelNames().stream()
            .filter(modelName -> isRoutableModelName(modelName, runtimeSupport.groupRepository().findAll(), channelsById))
            .toList();
    }

    private static boolean isRoutableModelName(String modelName, List<Group> groups, Map<Integer, Channel> channelsById) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return groups.stream()
            .filter(group -> group != null && group.items() != null)
            .flatMap(group -> group.items().stream())
            .anyMatch(item -> matchesRoutableItem(modelName, item, channelsById));
    }

    private static boolean matchesRoutableItem(String modelName, GroupItem item, Map<Integer, Channel> channelsById) {
        if (item == null || item.modelName() == null || !item.modelName().equals(modelName)) {
            return false;
        }
        return channelsById.containsKey(item.channelId());
    }

    private static boolean hasUsableBaseUrl(Channel channel) {
        return channel.baseUrls() != null
            && channel.baseUrls().stream().anyMatch(baseUrl -> baseUrl != null && baseUrl.url() != null && !baseUrl.url().isBlank());
    }

    private static boolean hasUsableChannelKey(List<ChannelKey> keys) {
        return keys != null && keys.stream().anyMatch(key -> key != null
            && Boolean.TRUE.equals(key.enabled())
            && key.channelKey() != null
            && !key.channelKey().isBlank());
    }

    private static Set<String> parseSupportedModels(APIKey apiKey) {
        if (apiKey == null || apiKey.supportedModels() == null || apiKey.supportedModels().isBlank()) {
            return null;
        }

        return Arrays.stream(apiKey.supportedModels().split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(Collectors.toSet());
    }
}
