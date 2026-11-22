package io.github.shigureyukina.octobus.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Serves the exported Next.js frontend from a configurable filesystem directory.
 *
 * <p>Mirrors the Go implementation's behavior for static assets while adding an
 * SPA fallback to {@code index.html} for frontend routes.
 */
@Configuration
class StaticFrontendRouter {

    private final WebProperties webProperties;

    StaticFrontendRouter(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Bean
    RouterFunction<ServerResponse> staticFrontendRoutes() {
        RequestPredicate predicate = request -> webProperties.enabled()
            && request.method() == HttpMethod.GET
            && isFrontendPath(request.path());

        return RouterFunctions.route(predicate, this::serveFrontend);
    }

    private Mono<ServerResponse> serveFrontend(ServerRequest request) {
        Path staticRoot = staticRoot();
        Resource requestedResource = resolveRequestedResource(request.path(), staticRoot);
        if (requestedResource != null) {
            return serveResource(requestedResource, true);
        }

        if (!shouldFallbackToIndex(request.path())) {
            return ServerResponse.notFound().build();
        }

        Resource indexResource = resolveIndexResource(staticRoot);
        if (indexResource == null) {
            return ServerResponse.notFound().build();
        }

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(indexResource);
    }

    private Mono<ServerResponse> serveResource(Resource resource, boolean immutableCache) {
        ServerResponse.BodyBuilder builder = ServerResponse.ok()
            .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM));
        if (immutableCache) {
            builder.cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable());
        }
        return builder.bodyValue(resource);
    }

    private Resource resolveRequestedResource(String requestPath, Path staticRoot) {
        String relativePath = stripLeadingSlash(requestPath);
        if (relativePath.isBlank()) {
            return null;
        }

        Path resolved = staticRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(staticRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return new FileSystemResource(resolved);
    }

    private Resource resolveIndexResource(Path staticRoot) {
        Path resolved = staticRoot.resolve(webProperties.indexFile()).normalize();
        if (!resolved.startsWith(staticRoot) || !Files.isRegularFile(resolved)) {
            return null;
        }
        return new FileSystemResource(resolved);
    }

    private Path staticRoot() {
        return Path.of(webProperties.staticDir()).toAbsolutePath().normalize();
    }

    private static boolean isFrontendPath(String path) {
        return !matchesPrefix(path, "/api")
            && !matchesPrefix(path, "/v1")
            && !matchesPrefix(path, "/api/internal")
            && !"/health".equals(path);
    }

    private static boolean shouldFallbackToIndex(String path) {
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.isBlank() || !lastSegment.contains(".");
    }

    private static boolean matchesPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
