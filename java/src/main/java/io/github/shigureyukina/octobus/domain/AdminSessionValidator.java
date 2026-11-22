package io.github.shigureyukina.octobus.domain;

public interface AdminSessionValidator {

    void validate(String username, long tokenVersion);
}
