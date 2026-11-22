package io.github.shigureyukina.octobus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "octobus.auth.jwt-secret=test-secret"
)
@AutoConfigureWebTestClient
class StaticFrontendIntegrationTests {

    private static final Path SQLITE_DB_PATH = createTempDatabasePath();
    private static final Path STATIC_DIR = createStaticDir();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("octobus.database.url", () -> "jdbc:sqlite:" + normalizePath(SQLITE_DB_PATH));
        registry.add("octobus.web.static-dir", () -> normalizePath(STATIC_DIR));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void rootServesIndexHtml() {
        webTestClient.get()
            .uri("/")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("Octobus Java Frontend Test"));
    }

    @Test
    void spaRouteFallsBackToIndexHtml() {
        webTestClient.get()
            .uri("/channel")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("Octobus Java Frontend Test"));
    }

    @Test
    void staticAssetIsServedWithImmutableCacheHeader() {
        webTestClient.get()
            .uri("/assets/app.js")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.valueOf("application/javascript"))
            .expectHeader().valueMatches("Cache-Control", ".*immutable.*")
            .expectBody(String.class)
            .value(body -> assertThat(body).contains("octobus-frontend-test"));
    }

    @Test
    void missingAssetDoesNotFallbackToIndex() {
        webTestClient.get()
            .uri("/assets/missing.js")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void apiRouteStillUsesExistingAuthenticationBoundary() {
        webTestClient.get()
            .uri("/api/v1/channel/list")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing bearer token");
    }

    @Test
    void relayRouteStillUsesExistingAuthenticationBoundary() {
        webTestClient.get()
            .uri("/v1/models")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo(401)
            .jsonPath("$.message").isEqualTo("Missing API key");
    }

    @Test
    void healthRouteIsNotHandledByFrontendFallback() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.code").isEqualTo(200)
            .jsonPath("$.message").isEqualTo("success")
            .jsonPath("$.data.status").isEqualTo("ok");
    }

    private static Path createStaticDir() {
        try {
            Path dir = Files.createTempDirectory("octobus-static-test");
            Files.createDirectories(dir.resolve("assets"));
            Files.writeString(dir.resolve("index.html"), "<html><body>Octobus Java Frontend Test</body></html>");
            Files.writeString(dir.resolve("assets").resolve("app.js"), "console.log('octobus-frontend-test');");
            return dir;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create static frontend test directory", ex);
        }
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("octobus-static-db-test").resolve("octobus-test.db");
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create SQLite test database path", ex);
        }
    }

    private static String normalizePath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
