package io.github.shigureyukina.octobus.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "octobus.database")
public record DatabaseProperties(
    Type type,
    @NotBlank String url,
    String username,
    String password
) {

    public enum Type {
        SQLITE,
        MYSQL
    }

    public boolean isMysqlLike() {
        return type == Type.MYSQL;
    }
}
