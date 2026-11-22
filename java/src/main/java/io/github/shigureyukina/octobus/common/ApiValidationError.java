package io.github.shigureyukina.octobus.common;

/**
 * Client-safe validation error detail for a single field or parameter.
 *
 * @param field validation target name
 * @param message validation failure message
 */
public record ApiValidationError(
    String field,
    String message
) {
}
