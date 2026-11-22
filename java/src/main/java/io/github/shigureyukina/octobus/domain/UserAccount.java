package io.github.shigureyukina.octobus.domain;

public record UserAccount(
    long id,
    String username,
    String passwordHash,
    long tokenVersion
) {
}
