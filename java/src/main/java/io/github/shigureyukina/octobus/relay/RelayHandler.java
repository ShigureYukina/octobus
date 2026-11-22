package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiResponse;
import java.util.function.BiFunction;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Signal;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux handler factory, parameterized by the inbound API format.
 *
 * <p>A fixed format is bound at construction time. The handler reads
 * the request body, short-circuits on {@code stream: true} (returning
 * 501 for stream-capable endpoints), and delegates the actual relay
 * lifecycle to a {@link RelayRun} produced by the configured factory.
 *
 * <p>Tests may substitute a custom {@code runFactory} to drive a
 * stubbed {@code RelayRun} through the handler without starting
 * the full application context.
 */
public class RelayHandler {

    @FunctionalInterface
    public interface RunFactory {
        RelayRun create(ServerRequest request, String rawBody, RelayInboundFormat format);
    }

    private final RelayInboundFormat format;
    private final RunFactory runFactory;
    private final ObjectMapper objectMapper;

    public RelayHandler(RelayInboundFormat format, ObjectMapper objectMapper) {
        this(format, defaultRunFactory(), objectMapper);
    }

    public RelayHandler(RelayInboundFormat format, RelayRuntimeSupport runtimeSupport, ObjectMapper objectMapper) {
        this(format, (request, body, inboundFormat) -> new RelayRun(request, body, inboundFormat, runtimeSupport), objectMapper);
    }

    public RelayHandler(
        RelayInboundFormat format,
        BiFunction<String, RelayInboundFormat, RelayRun> runFactory,
        ObjectMapper objectMapper
    ) {
        this(format, (request, body, inboundFormat) -> runFactory.apply(body, inboundFormat), objectMapper);
    }

    public RelayHandler(
        RelayInboundFormat format,
        RunFactory runFactory,
        ObjectMapper objectMapper
    ) {
        this.format = format;
        this.runFactory = runFactory;
        this.objectMapper = objectMapper;
    }

    public RelayInboundFormat format() {
        return format;
    }

    /**
     * Handle an inbound relay request.
     *
     * <p>Two early-exit paths exist in T1:
     * <ol>
     *   <li>{@code stream: true} on a stream-capable endpoint → 501
     *       with a predictable message.</li>
     *   <li>All other requests → the configured {@code RelayRun} is
     *       built and executed, currently producing a controlled
     *       "adapter not wired" error.</li>
     * </ol>
     */
    public Mono<ServerResponse> handle(ServerRequest request) {
        return request.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> process(request, body));
    }

    private Mono<ServerResponse> process(ServerRequest request, String body) {
        if (RelayInboundFormat.supportsStreaming(format) && hasStreamFlag(body)) {
            RelayRun run = runFactory.create(request, body, format);
            try {
                Flux<ServerSentEvent<String>> stream = run.executeStream();
                Flux<Signal<ServerSentEvent<String>>> replayed = stream.materialize().replay().autoConnect(1);
                return replayed.next().flatMap(signal -> {
                    if (signal.isOnError()) {
                        Throwable throwable = signal.getThrowable();
                        if (throwable instanceof io.github.shigureyukina.octobus.common.ApiException apiException) {
                            return jsonResponse(ResponseEntity
                                .status(apiException.getStatus())
                                .body(ApiResponse.error(apiException.getStatus(), apiException.getClientMessage())));
                        }
                        return jsonResponse(ResponseEntity
                            .status(HttpStatus.BAD_GATEWAY)
                            .body(ApiResponse.error(HttpStatus.BAD_GATEWAY,
                                throwable == null || throwable.getMessage() == null
                                    ? "Upstream stream failed"
                                    : throwable.getMessage())));
                    }
                    if (signal.isOnComplete()) {
                        return ServerResponse.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(Flux.empty(), ServerSentEvent.class);
                    }
                    return ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(replayed.dematerialize(), ServerSentEvent.class);
                });
            } catch (Exception exception) {
                if (exception instanceof io.github.shigureyukina.octobus.common.ApiException apiException) {
                    return jsonResponse(ResponseEntity
                        .status(apiException.getStatus())
                        .body(ApiResponse.error(apiException.getStatus(), apiException.getClientMessage())));
                }
                return jsonResponse(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Failed to parse relay request")));
            }
        }
        RelayRun run = runFactory.create(request, body, format);
        return run.execute().flatMap(this::jsonResponse);
    }

    private boolean hasStreamFlag(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode stream = node.get("stream");
            return stream != null && stream.isBoolean() && stream.asBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static RunFactory defaultRunFactory() {
        return (request, body, fmt) -> new RelayRun(body, fmt, RelayCandidateIterator.empty());
    }

    private Mono<ServerResponse> jsonResponse(ResponseEntity<?> entity) {
        return ServerResponse.status(entity.getStatusCode())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(entity.getBody());
    }
}
