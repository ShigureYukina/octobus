package io.github.shigureyukina.octobus;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.config.AuthProperties;
import io.github.shigureyukina.octobus.config.DatabaseProperties;
import io.github.shigureyukina.octobus.config.RelayProperties;
import io.github.shigureyukina.octobus.config.ServerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class OctobusApplicationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
    }

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private DatabaseProperties databaseProperties;

    @Autowired
    private RelayProperties relayProperties;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void applicationBootsWithHealthAndSharedFoundation() {
        assertThat(serverProperties.host()).isEqualTo("0.0.0.0");
        assertThat(serverProperties.port()).isEqualTo(8081);
        assertThat(databaseProperties.url()).startsWith("jdbc:sqlite:");
        assertThat(relayProperties.connectTimeout()).hasSeconds(5);
        assertThat(relayProperties.readTimeout()).hasSeconds(60);
        assertThat(relayProperties.retry().maxAttempts()).isEqualTo(2);
        assertThat(relayProperties.retry().backoff()).hasMillis(500);
        assertThat(relayProperties.circuitBreaker().failureThreshold()).isEqualTo(5);
        assertThat(relayProperties.circuitBreaker().openStateDuration()).hasSeconds(30);
        assertThat(relayProperties.circuitBreaker().maxCooldown()).hasSeconds(600);
        assertThat(authProperties.jwtSecret()).isEqualTo("test-secret");
        assertThat(dataSource).isNotNull();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        Integer tableCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            Integer.class,
            "app_bootstrap_probe"
        );
        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM app_user WHERE username = ?",
            Integer.class,
            "admin"
        );
        assertThat(tableCount).isEqualTo(1);
        assertThat(userCount).isEqualTo(1);

        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.message").isEqualTo("success")
            .jsonPath("$.data.status").isEqualTo("ok")
            .jsonPath("$.errors").doesNotExist();

        webTestClient.get()
            .uri("/api/v1/channel/list")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing bearer token")
            .jsonPath("$.data").doesNotExist()
            .jsonPath("$.errors").doesNotExist();
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-application-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
