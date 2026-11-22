package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.LogRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class LogService {

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final LogRepository logRepository;
    private final Sinks.Many<RelayLog> logSink;
    private final Set<String> streamTokens = ConcurrentHashMap.newKeySet();

    public LogService(LogRepository logRepository) {
        this.logRepository = logRepository;
        this.logSink = Sinks.many().multicast().onBackpressureBuffer(1024);
    }

    public LogListResponse list(LogFilter filter) {
        if (filter.pageSize() > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page_size must not exceed " + MAX_PAGE_SIZE);
        }

        long total = logRepository.count(filter);
        if (total == 0) {
            return new LogListResponse(List.of(), 0, filter.page(), filter.pageSize());
        }

        var items = logRepository.findAll(filter);
        return new LogListResponse(items, total, filter.page(), filter.pageSize());
    }

    public void clear() {
        logRepository.deleteAll();
    }

    public void pushLog(RelayLog log) {
        logSink.tryEmitNext(log);
    }

    public Flux<ServerSentEvent<String>> stream(String token) {
        if (token == null || !streamTokens.contains(token)) {
            return Flux.error(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or missing stream token"));
        }
        return logSink.asFlux()
                .map(log -> ServerSentEvent.<String>builder()
                        .data(log.id() + "|" + log.time() + "|" + log.channelName())
                        .build())
                .doFinally(signal -> { });
    }

    public String createStreamToken() {
        String token = UUID.randomUUID().toString();
        streamTokens.add(token);
        return token;
    }

    public void revokeStreamToken(String token) {
        streamTokens.remove(token);
    }
}
