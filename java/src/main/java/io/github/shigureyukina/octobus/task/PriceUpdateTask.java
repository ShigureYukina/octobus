package io.github.shigureyukina.octobus.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.persistence.ModelRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceUpdateTask {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateTask.class);
    private static final String PRICE_SOURCE = "https://models.dev/api.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ModelRepository modelRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PriceUpdateTask(ModelRepository modelRepository, ObjectMapper objectMapper) {
        this.modelRepository = modelRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Scheduled(fixedRate = 86_400_000) // every 24 hours
    public void updatePrices() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PRICE_SOURCE))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Price source returned status {}", response.statusCode());
                return;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.isArray()) {
                int count = 0;
                for (JsonNode item : root) {
                    String name = item.path("model").asText(null);
                    if (name == null || name.isBlank()) continue;
                    double input = item.path("input").asDouble(0);
                    double output = item.path("output").asDouble(0);
                    double cacheRead = item.path("cache_read").asDouble(0);
                    double cacheWrite = item.path("cache_write").asDouble(0);
                    try {
                        modelRepository.upsert(name, input, output, cacheRead, cacheWrite);
                        count++;
                    } catch (Exception exception) {
                        log.debug("Failed to upsert model {}: {}", name, exception.getMessage());
                    }
                }
                log.info("Updated prices for {} models from remote source", count);
            }
        } catch (Exception exception) {
            log.warn("Price update failed: {}", exception.getMessage());
        }
    }
}
