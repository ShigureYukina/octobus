package io.github.shigureyukina.octobus.relay.it;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Embedded stub upstream server for relay integration tests.
 *
 * <p>Must be started before tests (typically in {@code @BeforeAll}) and
 * stopped after (typically in {@code @AfterAll}).
 *
 * <p>Each test scenario enqueues expected responses via
 * {@link #enqueue(int, String)} in the order the relay will consume them.
 * Optionally call {@link #recordRequests()} before the test run to capture
 * upstream request bodies for later assertions.
 */
public final class StubUpstream implements AutoCloseable {

    private final HttpServer server;
    private final int port;
    private final BlockingQueue<StubResponse> responses = new LinkedBlockingQueue<>();
    private final List<String> recordedRequests = new ArrayList<>();
    private volatile boolean recording;

    public StubUpstream() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", new RelayHandler());
            server.setExecutor(null);
            server.start();
            this.port = server.getAddress().getPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start stub upstream", e);
        }
    }

    /** The port this stub is listening on. */
    public int port() {
        return port;
    }

    /** Base URL including port, without trailing slash. */
    public String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    /**
     * Enqueue a response to be returned for the next matching request.
     *
     * @param statusCode HTTP status code (e.g. 200, 429, 500)
     * @param body       response body as a JSON string
     */
    public StubUpstream enqueue(int statusCode, String body) {
        responses.add(new StubResponse(statusCode, body));
        return this;
    }

    /**
     * Start recording upstream request bodies.
     * Call before the test scenario; recorded requests are available via
     * {@link #recordedRequests()} and {@link #lastRequestBody()}.
     */
    public StubUpstream recordRequests() {
        this.recording = true;
        return this;
    }

    /** Immutable snapshot of all recorded request bodies. */
    public List<String> recordedRequests() {
        synchronized (recordedRequests) {
            return List.copyOf(recordedRequests);
        }
    }

    /** The last recorded request body, or {@code null} if none. */
    public String lastRequestBody() {
        synchronized (recordedRequests) {
            return recordedRequests.isEmpty() ? null : recordedRequests.get(recordedRequests.size() - 1);
        }
    }

    /** Clear the response queue and recorded requests. */
    public void reset() {
        responses.clear();
        synchronized (recordedRequests) {
            recordedRequests.clear();
        }
        recording = false;
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- internal ---

    private record StubResponse(int statusCode, String body) {}

    private class RelayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            if (recording) {
                String requestBody = new String(requestBytes, StandardCharsets.UTF_8);
                synchronized (recordedRequests) {
                    recordedRequests.add(requestBody);
                }
            }

            StubResponse response = responses.poll();
            if (response == null) {
                response = new StubResponse(503, "{\"error\":\"no stub response configured\"}");
            }

            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(response.statusCode(), body.length);
            } catch (IOException e) {
                // client may have disconnected; safe to ignore
                return;
            }
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }
}
