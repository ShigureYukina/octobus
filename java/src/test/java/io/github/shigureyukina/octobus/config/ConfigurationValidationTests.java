package io.github.shigureyukina.octobus.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.OctobusApplication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

class ConfigurationValidationTests {

    @Test
    void startupFailsWhenRequiredConfigIsBlank() {
        Exception ex = startupFailure(
            "spring.main.web-application-type=none",
            "server.port=0",
            "octobus.auth.jwt-secret="
        );

        assertThat(ex).hasMessageContaining("Could not bind properties to 'AuthProperties'");
        assertThat(ex.getCause()).isNotNull();
        assertThat(ex.getCause().getCause()).isNotNull();
        assertThat(ex.getCause().getCause().getMessage()).contains("octobus.auth");
    }

    @Test
    void startupFailsWhenRelayTimeoutIsNegative() {
        Exception ex = startupFailure(
            "spring.main.web-application-type=none",
            "server.port=0",
            "octobus.auth.jwt-secret=test-secret",
            "octobus.relay.connect-timeout=-1s"
        );

        assertThat(ex).hasMessageContaining("Could not bind properties to 'RelayProperties'");
        assertThat(ex).hasStackTraceContaining("octobus.relay.connectTimeout");
    }

    private Exception startupFailure(String... properties) {
        String[] args = new String[properties.length + 1];
        for (int i = 0; i < properties.length; i++) {
            args[i] = "--" + properties[i];
        }
        args[properties.length] = "--octobus.database.url=jdbc:sqlite:" + normalizePath(createTempDatabasePath());

        try {
            new SpringApplicationBuilder(OctobusApplication.class)
                .run(args)
                .close();
        } catch (Exception ex) {
            return ex;
        }

        throw new AssertionError("Expected startup to fail for invalid configuration");
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-config-validation-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
