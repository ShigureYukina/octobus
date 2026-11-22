package io.github.shigureyukina.octobus.common;

import java.util.List;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.bind.support.WebExchangeBindException;

/**
 * Centralized exception-to-response mapping for WebFlux controllers.
 */
@Order(-2)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        return ResponseEntity
            .status(ex.getStatus())
            .body(ApiResponse.error(ex.getStatus(), ex.getClientMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebExchangeBindException(WebExchangeBindException ex) {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.validationError(mapFieldErrors(ex.getFieldErrors())));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ApiValidationError> errors = ex.getConstraintViolations()
            .stream()
            .map(violation -> new ApiValidationError(resolveConstraintViolationField(violation.getPropertyPath()), violation.getMessage()))
            .toList();

        return ResponseEntity.badRequest().body(ApiResponse.validationError(errors));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleServerWebInputException(ServerWebInputException ex) {
        String field = resolveInputExceptionField(ex);
        if (field != null) {
            return ResponseEntity
                .badRequest()
                .body(ApiResponse.validationError(List.of(new ApiValidationError(field, "Invalid value"))));
        }

        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Invalid request parameters"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolvedStatus = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        String message = ex.getReason() != null && !ex.getReason().isBlank()
            ? ex.getReason()
            : resolvedStatus.getReasonPhrase();

        return ResponseEntity
            .status(resolvedStatus)
            .body(ApiResponse.error(resolvedStatus, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error handling request", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.internalServerError());
    }

    private List<ApiValidationError> mapFieldErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
            .map(fieldError -> new ApiValidationError(fieldError.getField(), resolveFieldErrorMessage(fieldError)))
            .collect(Collectors.toList());
    }

    private String resolveFieldErrorMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()
            ? fieldError.getDefaultMessage()
            : "Invalid value";
    }

    private String resolveConstraintViolationField(Path propertyPath) {
        String leaf = null;

        for (Path.Node node : propertyPath) {
            if (node.getName() != null && !node.getName().isBlank()) {
                leaf = node.getName();
            }
        }

        return leaf != null ? leaf : propertyPath.toString();
    }

    private String resolveInputExceptionField(ServerWebInputException ex) {
        if (ex.getMethodParameter() == null) {
            return null;
        }

        if (ex.getMethodParameter().hasParameterAnnotation(RequestBody.class)) {
            return "body";
        }

        return ex.getMethodParameter().getParameterName();
    }
}
