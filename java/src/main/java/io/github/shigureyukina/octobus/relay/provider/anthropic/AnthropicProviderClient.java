package io.github.shigureyukina.octobus.relay.provider.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelKey;
import io.github.shigureyukina.octobus.domain.CustomHeader;
import io.github.shigureyukina.octobus.relay.RelayAttemptResult;
import io.github.shigureyukina.octobus.relay.provider.ProviderHttpClient;
import io.github.shigureyukina.octobus.relay.spi.OutboundTransformer;
import io.github.shigureyukina.octobus.relay.spi.UnifiedResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AnthropicProviderClient implements ProviderHttpClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AnthropicProviderClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<RelayAttemptResult<JsonNode>> postChatCompletion(
        Channel channel,
        ChannelKey channelKey,
        JsonNode payload,
        OutboundTransformer outboundTransformer
    ) {
        URI endpoint = buildMessagesUri(channel);
        return webClient.post()
            .uri(endpoint)
            .headers(headers -> applyHeaders(headers, channelKey, channel.customHeader()))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .exchangeToMono(response -> response.bodyToMono(String.class)
                .defaultIfEmpty("{}")
                .map(body -> toAttemptResult(response.statusCode().value(), body, outboundTransformer)))
            .onErrorResume(exception -> Mono.just(RelayAttemptResult.failure(
                "Upstream request failed: " + exception.getMessage(),
                0
            )));
    }

    @Override
    public Flux<ServerSentEvent<String>> executeStream(
        Channel channel,
        ChannelKey channelKey,
        JsonNode payload,
        OutboundTransformer outboundTransformer
    ) {
        URI endpoint = buildMessagesUri(channel);
        return webClient.post()
            .uri(endpoint)
            .headers(headers -> applyHeaders(headers, channelKey, channel.customHeader()))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(payload)
            .exchangeToFlux(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMapMany(body -> Flux.error(new ApiException(
                            mapStreamErrorStatus(response.statusCode().value()),
                            "Upstream returned status " + response.statusCode().value()
                                + (body.isBlank() ? "" : ": " + body)
                        )));
                }
                return decodeSse(response.bodyToFlux(DataBuffer.class));
            });
    }

    private HttpStatus mapStreamErrorStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private Flux<ServerSentEvent<String>> decodeSse(Flux<DataBuffer> chunks) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();
            StringBuilder eventData = new StringBuilder();
            StringBuilder eventType = new StringBuilder();
            AtomicBoolean done = new AtomicBoolean(false);

            chunks.subscribe(chunk -> {
                if (done.get()) {
                    DataBufferUtils.release(chunk);
                    return;
                }
                try {
                    buffer.append(readChunk(chunk));
                } finally {
                    DataBufferUtils.release(chunk);
                }
                drainBuffer(buffer, eventData, eventType, sink, done);
            }, sink::error, () -> {
                if (!done.get()) {
                    flushEvent(eventData, eventType, sink, done);
                    sink.complete();
                }
            });
        });
    }

    private void drainBuffer(
        StringBuilder buffer,
        StringBuilder eventData,
        StringBuilder eventType,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done
    ) {
        int delimiterIndex;
        while (!done.get() && (delimiterIndex = buffer.indexOf("\n\n")) >= 0) {
            String frame = buffer.substring(0, delimiterIndex);
            buffer.delete(0, delimiterIndex + 2);
            parseFrame(frame, eventData, eventType, sink, done);
        }
    }

    private void parseFrame(
        String frame,
        StringBuilder eventData,
        StringBuilder eventType,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done
    ) {
        String normalized = frame.replace("\r", "");
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("event:")) {
                eventType.setLength(0);
                eventType.append(line.substring(6).stripLeading());
            } else if (line.startsWith("data:")) {
                String value = line.substring(5).stripLeading();
                if (eventData.length() > 0) {
                    eventData.append('\n');
                }
                eventData.append(value);
            }
        }
        flushEvent(eventData, eventType, sink, done);
    }

    private void flushEvent(
        StringBuilder eventData,
        StringBuilder eventType,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done
    ) {
        if (eventData.length() == 0 || done.get()) {
            return;
        }
        String data = eventData.toString();
        String type = eventType.length() > 0 ? eventType.toString() : null;
        sink.next(ServerSentEvent.<String>builder()
            .data(data)
            .event(type)
            .build());
        eventData.setLength(0);
        eventType.setLength(0);
        if ("message_stop".equals(type)) {
            done.set(true);
            sink.complete();
        }
    }

    private String readChunk(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private RelayAttemptResult<JsonNode> toAttemptResult(int statusCode, String body, OutboundTransformer outboundTransformer) {
        if (statusCode < 200 || statusCode >= 300) {
            return mapError(statusCode, body);
        }

        try {
            UnifiedResponse response = outboundTransformer.parseResponse(body);
            JsonNode rawPayload = response.rawPayload() != null
                ? response.rawPayload()
                : objectMapper.readTree(body);
            return RelayAttemptResult.success(rawPayload, statusCode);
        } catch (Exception exception) {
            return RelayAttemptResult.failure("Failed to parse upstream response: " + exception.getMessage(), statusCode);
        }
    }

    private RelayAttemptResult<JsonNode> mapError(int statusCode, String body) {
        String upstreamMessage = extractAnthropicErrorMessage(body);
        return switch (statusCode) {
            case 400 -> RelayAttemptResult.failure(upstreamMessage, 400);
            case 401 -> RelayAttemptResult.failure("Invalid upstream API key", 401);
            case 429 -> RelayAttemptResult.failure(upstreamMessage, 429);
            default -> RelayAttemptResult.failure("Upstream error", 502);
        };
    }

    private String extractAnthropicErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            String message = root.path("error").path("message").asText(null);
            return message == null || message.isBlank() ? "Upstream error" : message;
        } catch (Exception exception) {
            return "Upstream error";
        }
    }

    private URI buildMessagesUri(Channel channel) {
        String baseUrl = selectBaseUrl(channel.baseUrls());
        if (baseUrl.endsWith("/v1/messages")) {
            return URI.create(baseUrl);
        }
        if (baseUrl.endsWith("/v1")) {
            return URI.create(baseUrl + "/messages");
        }
        if (baseUrl.endsWith("/")) {
            return URI.create(baseUrl + "v1/messages");
        }
        return URI.create(baseUrl + "/v1/messages");
    }

    private String selectBaseUrl(List<io.github.shigureyukina.octobus.domain.BaseUrl> baseUrls) {
        if (baseUrls == null || baseUrls.isEmpty()) {
            throw new IllegalArgumentException("Channel has no base URL configured");
        }
        return baseUrls.stream()
            .filter(baseUrl -> baseUrl != null && baseUrl.url() != null && !baseUrl.url().isBlank())
            .sorted((left, right) -> Integer.compare(
                left.delay() == null ? Integer.MAX_VALUE : left.delay(),
                right.delay() == null ? Integer.MAX_VALUE : right.delay()
            ))
            .findFirst()
            .map(io.github.shigureyukina.octobus.domain.BaseUrl::url)
            .orElseThrow(() -> new IllegalArgumentException("Channel has no usable base URL configured"));
    }

    private void applyHeaders(HttpHeaders headers, ChannelKey channelKey, List<CustomHeader> customHeaders) {
        headers.set("x-api-key", channelKey.channelKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);
        if (customHeaders == null) {
            return;
        }
        for (CustomHeader customHeader : customHeaders) {
            if (customHeader == null || customHeader.headerKey() == null || customHeader.headerKey().isBlank()) {
                continue;
            }
            headers.set(customHeader.headerKey(), customHeader.headerValue() == null ? "" : customHeader.headerValue());
        }
    }
}
