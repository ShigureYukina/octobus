package io.github.shigureyukina.octobus.common;

import org.springframework.http.HttpStatus;

/**
 * Typed application exception for stable client-facing API errors.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String clientMessage;

    public ApiException(HttpStatus status, String clientMessage) {
        super(clientMessage);
        this.status = status;
        this.clientMessage = clientMessage;
    }

    public ApiException(HttpStatus status, String clientMessage, Throwable cause) {
        super(clientMessage, cause);
        this.status = status;
        this.clientMessage = clientMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getClientMessage() {
        return clientMessage;
    }
}
