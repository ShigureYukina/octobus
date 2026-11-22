package io.github.shigureyukina.octobus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * Maps filter-layer ApiException instances to the shared response envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (!(ex instanceof ApiException apiException)) {
            return Mono.error(ex);
        }

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        response.setStatusCode(apiException.getStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return Mono.fromCallable(() -> objectMapper.writeValueAsBytes(ApiResponse.error(apiException.getStatus(), apiException.getClientMessage())))
            .flatMap(bytes -> response.writeWith(Mono.just(response.bufferFactory().wrap(bytes))))
            .onErrorResume(writeError -> {
                response.setStatusCode(apiException.getStatus());
                byte[] fallback = ("{\"code\":" + apiException.getStatus().value() + ",\"message\":\"" + apiException.getClientMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
            });
    }
}
