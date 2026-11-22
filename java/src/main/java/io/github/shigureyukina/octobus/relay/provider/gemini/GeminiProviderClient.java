package io.github.shigureyukina.octobus.relay.provider.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
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
public class GeminiProviderClient implements ProviderHttpClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GeminiProviderClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
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
        RequestSpec requestSpec = prepareRequest(channel, payload, false);
        return webClient.post()
            .uri(requestSpec.endpoint())
            .headers(headers -> applyHeaders(headers, channelKey, channel.customHeader()))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestSpec.body())
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
        RequestSpec requestSpec = prepareRequest(channel, payload, true);
        return webClient.post()
            .uri(requestSpec.endpoint())
            .headers(headers -> applyHeaders(headers, channelKey, channel.customHeader()))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(requestSpec.body())
            .exchangeToFlux(response -> {
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMapMany(body -> Flux.error(new ApiException(
                            mapStreamErrorStatus(response.statusCode().value()),
                            extractGeminiErrorMessage(body, response.statusCode().value())
                        )));
                }
                return decodeGeminiSse(response.bodyToFlux(DataBuffer.class), requestSpec.model());
            });
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
        String message = extractGeminiErrorMessage(body, statusCode);
        return switch (statusCode) {
            case 400 -> RelayAttemptResult.failure(message, 400);
            case 401 -> RelayAttemptResult.failure("Invalid upstream API key", 401);
            case 403 -> RelayAttemptResult.failure(message, 403);
            case 404 -> RelayAttemptResult.failure(message, 404);
            case 429 -> RelayAttemptResult.failure(message, 429);
            default -> RelayAttemptResult.failure("Upstream error", 502);
        };
    }

    private HttpStatus mapStreamErrorStatus(int statusCode) {
        return switch (statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private String extractGeminiErrorMessage(String body, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            String message = root.path("error").path("message").asText(null);
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return switch (statusCode) {
            case 401 -> "Invalid upstream API key";
            case 404 -> "Upstream model not found";
            default -> "Upstream error";
        };
    }

    private Flux<ServerSentEvent<String>> decodeGeminiSse(Flux<DataBuffer> chunks, String model) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();
            StringBuilder eventData = new StringBuilder();
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
                drainBuffer(buffer, eventData, sink, done, model);
            }, sink::error, () -> {
                if (!done.get()) {
                    emitFinishChunk(sink, model);
                    emitDone(sink, done);
                }
            });
        });
    }

    private void drainBuffer(
        StringBuilder buffer,
        StringBuilder eventData,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done,
        String model
    ) {
        int delimiterIndex;
        while (!done.get() && (delimiterIndex = buffer.indexOf("\n\n")) >= 0) {
            String frame = buffer.substring(0, delimiterIndex);
            buffer.delete(0, delimiterIndex + 2);
            parseFrame(frame, eventData, sink, done, model);
        }
    }

    private void parseFrame(
        String frame,
        StringBuilder eventData,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done,
        String model
    ) {
        String normalized = frame.replace("\r", "");
        String[] lines = normalized.split("\n", -1);
        for (String line : lines) {
            if (line.startsWith("data:")) {
                String value = line.substring(5).stripLeading();
                if (eventData.length() > 0) {
                    eventData.append('\n');
                }
                eventData.append(value);
            }
        }
        flushEvent(eventData, sink, done, model);
    }

    private void flushEvent(
        StringBuilder eventData,
        reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink,
        AtomicBoolean done,
        String model
    ) {
        if (eventData.length() == 0 || done.get()) {
            return;
        }

        String data = eventData.toString();
        eventData.setLength(0);
        if ("[DONE]".equals(data)) {
            emitDone(sink, done);
            return;
        }

        try {
            JsonNode chunk = objectMapper.readTree(data);
            String text = extractChunkText(chunk);
            if (text != null && !text.isEmpty()) {
                sink.next(ServerSentEvent.<String>builder()
                    .data(toOpenAiChunk(model, text, null))
                    .build());
            }
            if (hasFinishReason(chunk)) {
                emitFinishChunk(sink, model);
                emitDone(sink, done);
            }
        } catch (Exception exception) {
            sink.error(exception);
        }
    }

    private boolean hasFinishReason(JsonNode chunk) {
        return chunk.path("candidates").isArray()
            && chunk.path("candidates").size() > 0
            && !chunk.path("candidates").get(0).path("finishReason").isMissingNode()
            && !chunk.path("candidates").get(0).path("finishReason").isNull();
    }

    private String extractChunkText(JsonNode chunk) {
        String deltaText = chunk.path("delta").path("text").asText(null);
        if (deltaText != null) {
            return deltaText;
        }
        JsonNode parts = chunk.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText(null);
            if (text != null) {
                builder.append(text);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String toOpenAiChunk(String model, String content, String finishReason) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "gemini-stream");
        root.put("object", "chat.completion.chunk");
        root.put("created", Instant.now().getEpochSecond());
        root.put("model", model == null ? "gemini" : model);
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode delta = choice.putObject("delta");
        if (content != null) {
            delta.put("content", content);
        }
        if (finishReason == null) {
            choice.putNull("finish_reason");
        } else {
            choice.put("finish_reason", finishReason);
        }
        return root.toString();
    }

    private void emitFinishChunk(reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink, String model) {
        sink.next(ServerSentEvent.<String>builder()
            .data(toOpenAiChunk(model, null, "stop"))
            .build());
    }

    private void emitDone(reactor.core.publisher.FluxSink<ServerSentEvent<String>> sink, AtomicBoolean done) {
        done.set(true);
        sink.next(ServerSentEvent.<String>builder().data("[DONE]").build());
        sink.complete();
    }

    private RequestSpec prepareRequest(Channel channel, JsonNode payload, boolean stream) {
        if (!(payload instanceof ObjectNode payloadObject)) {
            throw new IllegalArgumentException("Gemini payload must be a JSON object");
        }
        JsonNode modelNode = payloadObject.get("model");
        String model = modelNode == null || modelNode.isNull() ? null : modelNode.asText();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Gemini payload missing model");
        }

        ObjectNode body = payloadObject.deepCopy();
        body.remove("model");
        URI endpoint = buildEndpointUri(channel, model, stream);
        return new RequestSpec(endpoint, body, model);
    }

    private URI buildEndpointUri(Channel channel, String model, boolean stream) {
        String baseUrl = selectBaseUrl(channel.baseUrls());
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = "/v1beta/models/" + model + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent");
        if (normalizedBase.endsWith("/v1beta")) {
            return URI.create(normalizedBase + "/models/" + model + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent"));
        }
        return URI.create(normalizedBase + suffix);
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
        headers.set("x-goog-api-key", channelKey.channelKey());
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

    private String readChunk(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record RequestSpec(URI endpoint, JsonNode body, String model) {
    }
}
