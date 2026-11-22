package io.github.shigureyukina.octobus.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Shared HTTP response envelope for admin-style endpoints.
 *
 * @param code HTTP status code mirrored into the response body
 * @param message stable client-facing message
 * @param data success payload when present
 * @param errors optional validation details for actionable client errors
 * @param <T> response payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    int code,
    String message,
    T data,
    List<ApiValidationError> errors
) {

    private static final String SUCCESS_MESSAGE = "success";
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request parameters";
    private static final String INTERNAL_SERVER_MESSAGE = "An unexpected error occurred";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), SUCCESS_MESSAGE, data, null);
    }

    public static ApiResponse<Void> error(HttpStatus status, String message) {
        return new ApiResponse<>(status.value(), message, null, null);
    }

    public static ApiResponse<Void> validationError(List<ApiValidationError> errors) {
        return new ApiResponse<>(HttpStatus.BAD_REQUEST.value(), INVALID_REQUEST_MESSAGE, null, errors);
    }

    public static ApiResponse<Void> internalServerError() {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_MESSAGE);
    }
}
