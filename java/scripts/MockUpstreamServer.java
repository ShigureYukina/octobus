import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone mock LLM upstream server for relay throughput testing.
 *
 * <p>Simulates an OpenAI-compatible chat completions endpoint.
 * Responds with a fixed (but valid-looking) response on every request.
 *
 * <p>Usage:
 * <pre>
 *   # Compile (no external deps)
 *   javac java/scripts/MockUpstreamServer.java -d java/scripts/
 *
 *   # Run on default port 8088
 *   java -cp java/scripts MockUpstreamServer
 *
 *   # Custom port
 *   java -cp java/scripts MockUpstreamServer 9090
 * </pre>
 */
public class MockUpstreamServer {

    private static final int DEFAULT_PORT = 8088;
    private static final byte[] RESPONSE_BODY = (
        "{\"id\":\"chatcmpl-mock001\",\"object\":\"chat.completion\",\"created\":1717200000," +
        "\"model\":\"gpt-4o-mock\",\"choices\":[{" +
        "\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"This is a mock response for load testing.\"}," +
        "\"finish_reason\":\"stop\"}]," +
        "\"usage\":{\"prompt_tokens\":50,\"completion_tokens\":10,\"total_tokens\":60}}"
    ).getBytes(StandardCharsets.UTF_8);

    private static final byte[] STREAM_CHUNK = (
        "data: {\"id\":\"chatcmpl-mock001\",\"object\":\"chat.completion.chunk\",\"created\":1717200000," +
        "\"model\":\"gpt-4o-mock\",\"choices\":[{" +
        "\"index\":0,\"delta\":{\"content\":\"mock \"},\"finish_reason\":null}]}\n\n"
    ).getBytes(StandardCharsets.UTF_8);

    private static final byte[] STREAM_DONE = "data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8);

    private final HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();

    public MockUpstreamServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(64));
        this.port = port;
    }

    private final int port;

    public void start() {
        server.start();
        System.out.println("[MockUpstream] Listening on :" + port);
        System.out.println("[MockUpstream] Base URL: http://127.0.0.1:" + port);
    }

    public void stop() {
        server.stop(1);
    }

    public int requestCount() {
        return requestCount.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        int count = requestCount.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // Read and discard request body
        try (var is = exchange.getRequestBody()) {
            is.readAllBytes();
        }

        boolean isStream = false;
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            isStream = true;
        }
        // Also check request body for stream=true (simplified: accept header is sufficient)

        long startNs = System.nanoTime();

        if (isStream) {
            // Simulate streaming response with slight delay per chunk
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            for (int i = 0; i < 5; i++) {
                os.write(STREAM_CHUNK);
                os.flush();
                try { Thread.sleep(5); } catch (InterruptedException ignored) { break; }
            }
            os.write(STREAM_DONE);
            os.flush();
            os.close();
        } else {
            // Non-streaming: respond immediately
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, RESPONSE_BODY.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(RESPONSE_BODY);
            }
        }

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        System.out.printf("[MockUpstream] #%d %s %s -> 200 (%dms)%s%n",
            count, method, path, elapsedMs, isStream ? " [stream]" : "");
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        var server = new MockUpstreamServer(port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[MockUpstream] Shutting down. Total requests: " + server.requestCount());
            server.stop();
        }));
    }
}
