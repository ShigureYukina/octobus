package io.github.shigureyukina.octobus.relay;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outcome of a single candidate attempt.
 *
 * <p>Successful outcomes carry an opaque response body so that the
 * non-stream handler can pass it through to the client. Failed outcomes
 * carry a stable message that surfaces in the {@code ApiResponse}
 * envelope and in the future {@code RelayLog} row.
 *
 * @param success true when the attempt produced a usable response
 * @param error stable error message; null on success
 * @param statusCode upstream HTTP status code (0 when not yet known)
 * @param body opaque response body; null on failure
 * @param <T> body type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelayAttemptResult<T>(
    boolean success,
    String error,
    int statusCode,
    T body
) {

    public static <T> RelayAttemptResult<T> success(T body) {
        return new RelayAttemptResult<>(true, null, 200, body);
    }

    public static <T> RelayAttemptResult<T> success(T body, int statusCode) {
        return new RelayAttemptResult<>(true, null, statusCode, body);
    }

    public static <T> RelayAttemptResult<T> failure(String error) {
        return new RelayAttemptResult<>(false, error, 0, null);
    }

    public static <T> RelayAttemptResult<T> failure(String error, int statusCode) {
        return new RelayAttemptResult<>(false, error, statusCode, null);
    }
}
