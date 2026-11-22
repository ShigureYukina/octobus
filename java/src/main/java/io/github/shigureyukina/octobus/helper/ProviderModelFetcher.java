package io.github.shigureyukina.octobus.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.AnthropicModelList;
import io.github.shigureyukina.octobus.domain.BaseUrl;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.CustomHeader;
import io.github.shigureyukina.octobus.domain.GeminiModelList;
import io.github.shigureyukina.octobus.domain.OpenAIModelList;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Fetches the list of available models from an upstream LLM provider.
 */
@Component
public class ProviderModelFetcher {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ProviderModelFetcher(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        var httpClient = HttpClient.create()
            .responseTimeout(REQUEST_TIMEOUT);
        this.webClient = webClientBuilder
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the model list from the upstream provider for the given channel.
     *
     * @param channel the channel configuration (base URLs, keys, type, etc.)
     * @return list of model names from the upstream
     * @throws ApiException if the request fails or the response cannot be parsed
     */
    public List<String> fetchModels(Channel channel) {
        var baseUrl = selectBaseUrl(channel.baseUrls());
        var models = fetchModelsFromProvider(baseUrl, channel);

        var matchRegex = channel.matchRegex();
        if (matchRegex != null && !matchRegex.isBlank()) {
            try {
                var pattern = Pattern.compile(matchRegex);
                models = models.stream()
                    .filter(m -> pattern.matcher(m).find())
                    .toList();
            } catch (PatternSyntaxException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid match_regex pattern: " + e.getMessage());
            }
        }

        return models;
    }

    private List<String> fetchModelsFromProvider(String baseUrl, Channel channel) {
        var type = channel.type();
        if (type != null && type.contains("anthropic")) {
            return fetchAnthropicModels(baseUrl, channel);
        }
        if (type != null && type.contains("gemini")) {
            return fetchGeminiModels(baseUrl, channel);
        }
        // Default: OpenAI-compatible
        return fetchOpenAIModels(baseUrl, channel);
    }

    private List<String> fetchGeminiModels(String baseUrl, Channel channel) {
        var key = selectFirstEnabledKey(channel);
        var allModels = new ArrayList<String>();
        String pageToken = null;

        while (true) {
            var uriStr = buildGeminiModelsUri(baseUrl).toString();
            if (pageToken != null && !pageToken.isBlank()) {
                uriStr = uriStr + "?pageToken=" + pageToken;
            }

            var response = webClient.get()
                .uri(URI.create(uriStr))
                .headers(headers -> applyGeminiHeaders(headers, key, channel.customHeader()))
                .retrieve()
                .bodyToMono(GeminiModelList.class)
                .onErrorMap(WebClientResponseException.class, ex ->
                    new ApiException(HttpStatus.valueOf(ex.getStatusCode().value()),
                        "Upstream returned " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString())
                )
                .block(REQUEST_TIMEOUT);

            if (response == null || response.models() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Empty response from Gemini models endpoint");
            }

            for (var m : response.models()) {
                if (m != null && m.name() != null) {
                    allModels.add(m.name().replaceFirst("^models/", ""));
                }
            }

            if (response.nextPageToken() == null || response.nextPageToken().isBlank()) {
                break;
            }
            pageToken = response.nextPageToken();
        }

        if (allModels.isEmpty()) {
            return fetchOpenAIModels(baseUrl, channel);
        }
        return allModels;
    }

    private List<String> fetchOpenAIModels(String baseUrl, Channel channel) {
        var uri = buildModelsUri(baseUrl);
        var key = selectFirstEnabledKey(channel);

        var response = webClient.get()
            .uri(uri)
            .headers(headers -> applyOpenAIHeaders(headers, key, channel.customHeader()))
            .retrieve()
            .bodyToMono(OpenAIModelList.class)
            .onErrorMap(WebClientResponseException.class, ex ->
                new ApiException(HttpStatus.valueOf(ex.getStatusCode().value()),
                    "Upstream returned " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString())
            )
            .block(REQUEST_TIMEOUT);

        if (response == null || response.data() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Empty response from upstream models endpoint");
        }

        return response.data().stream()
            .map(m -> m.id())
            .toList();
    }

    private List<String> fetchAnthropicModels(String baseUrl, Channel channel) {
        var key = selectFirstEnabledKey(channel);
        var allModels = new ArrayList<String>();
        String afterId = null;

        while (true) {
            var uriStr = buildModelsUri(baseUrl).toString();
            if (afterId != null) {
                uriStr = uriStr + "?after_id=" + afterId;
            }
            var requestBuilder = webClient.get()
                .uri(URI.create(uriStr))
                .headers(headers -> applyAnthropicHeaders(headers, key, channel.customHeader()));

            var response = requestBuilder.retrieve()
                .bodyToMono(AnthropicModelList.class)
                .onErrorMap(WebClientResponseException.class, ex ->
                    new ApiException(HttpStatus.valueOf(ex.getStatusCode().value()),
                        "Upstream returned " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString())
                )
                .block(REQUEST_TIMEOUT);

            if (response == null || response.data() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Empty response from Anthropic models endpoint");
            }

            for (var m : response.data()) {
                allModels.add(m.id());
            }

            if (response.hasMore() == null || !response.hasMore()) {
                break;
            }
            afterId = response.lastId();
        }

        return allModels;
    }

    private URI buildModelsUri(String baseUrl) {
        String normalized = baseUrl;
        if (normalized.endsWith("/v1/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
        } else if (normalized.endsWith("/v1/messages")) {
            normalized = normalized.substring(0, normalized.length() - "/messages".length());
        } else if (normalized.endsWith("/v1")) {
            // keep as-is
        } else if (normalized.endsWith("/")) {
            normalized = normalized + "v1";
        } else {
            normalized = normalized + "/v1";
        }
        return URI.create(normalized + "/models");
    }

    private URI buildGeminiModelsUri(String baseUrl) {
        String normalized = baseUrl;
        if (normalized.endsWith("/v1beta")) {
            return URI.create(normalized + "/models");
        }
        if (normalized.endsWith("/v1")) {
            return URI.create(normalized + "/models");
        }
        if (normalized.endsWith("/")) {
            normalized = normalized + "v1beta";
        } else {
            normalized = normalized + "/v1beta";
        }
        return URI.create(normalized + "/models");
    }

    private String selectBaseUrl(List<BaseUrl> baseUrls) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Channel has no base URL configured");
        }
        return baseUrls.stream()
            .filter(u -> u != null && u.url() != null && !u.url().isBlank())
            .sorted((left, right) -> Integer.compare(
                left.delay() == null ? Integer.MAX_VALUE : left.delay(),
                right.delay() == null ? Integer.MAX_VALUE : right.delay()
            ))
            .findFirst()
            .map(BaseUrl::url)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Channel has no usable base URL configured"));
    }

    private io.github.shigureyukina.octobus.domain.ChannelKey selectFirstEnabledKey(Channel channel) {
        return channel.keys().stream()
            .filter(k -> k != null && k.enabled() != null && k.enabled() && k.channelKey() != null && !k.channelKey().isBlank())
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Channel has no enabled API key configured"));
    }

    private void applyOpenAIHeaders(HttpHeaders headers,
                                    io.github.shigureyukina.octobus.domain.ChannelKey key,
                                    List<CustomHeader> customHeaders) {
        headers.setBearerAuth(key.channelKey());
        applyCustomHeaders(headers, customHeaders);
    }

    private void applyAnthropicHeaders(HttpHeaders headers,
                                       io.github.shigureyukina.octobus.domain.ChannelKey key,
                                       List<CustomHeader> customHeaders) {
        headers.set("x-api-key", key.channelKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        applyCustomHeaders(headers, customHeaders);
    }

    private void applyGeminiHeaders(HttpHeaders headers,
                                    io.github.shigureyukina.octobus.domain.ChannelKey key,
                                    List<CustomHeader> customHeaders) {
        headers.set("X-Goog-Api-Key", key.channelKey());
        applyCustomHeaders(headers, customHeaders);
    }

    private void applyCustomHeaders(HttpHeaders headers, List<CustomHeader> customHeaders) {
        if (customHeaders == null) return;
        for (var ch : customHeaders) {
            if (ch == null || ch.headerKey() == null || ch.headerKey().isBlank()) continue;
            headers.set(ch.headerKey(), ch.headerValue() == null ? "" : ch.headerValue());
        }
    }
}
