package io.github.shigureyukina.octobus.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shigureyukina.octobus.persistence.ModelRepository;
import io.github.shigureyukina.octobus.relay.metrics.PriceLookup;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the model table with built-in price presets on first startup.
 *
 * <p>Reads {@code presets.json} from the classpath and inserts all entries
 * into the database only when the model table is empty. This mirrors the
 * Go-side behavior where {@code presets.go} provides 200+ default prices
 * compiled into the binary.
 *
 * <p>Existing DB records are never overwritten: once the user modifies a
 * price via the API or a remote update runs, the presets are only used as
 * fallback for unknown models (by inserting when the table was first empty).
 */
@Component
public class PricePresetSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PricePresetSeeder.class);
    private static final String PRESETS_RESOURCE = "/presets.json";

    private final ModelRepository modelRepository;
    private final PriceLookup priceLookup;
    private final ObjectMapper objectMapper;

    public PricePresetSeeder(
        ModelRepository modelRepository,
        PriceLookup priceLookup,
        ObjectMapper objectMapper
    ) {
        this.modelRepository = modelRepository;
        this.priceLookup = priceLookup;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        if (!modelRepository.findAllNames().isEmpty()) {
            log.debug("Model table already populated, skipping preset seeding");
            return;
        }

        Map<String, PriceEntry> presets = loadPresets();
        if (presets == null || presets.isEmpty()) {
            log.warn("No price presets found in {}", PRESETS_RESOURCE);
            return;
        }

        int seeded = 0;
        for (var entry : presets.entrySet()) {
            String name = entry.getKey();
            PriceEntry price = entry.getValue();
            try {
                modelRepository.create(
                    name.toLowerCase(),
                    price.input,
                    price.output,
                    price.cacheRead,
                    price.cacheWrite
                );
                seeded++;
            } catch (Exception exception) {
                log.warn("Failed to seed preset model '{}': {}", name, exception.getMessage());
            }
        }

        priceLookup.refresh();
        log.info("Seeded {} built-in price presets into database", seeded);
    }

    private Map<String, PriceEntry> loadPresets() {
        try (InputStream stream = getClass().getResourceAsStream(PRESETS_RESOURCE)) {
            if (stream == null) {
                log.warn("Resource {} not found", PRESETS_RESOURCE);
                return null;
            }
            return objectMapper.readValue(stream, new TypeReference<>() {});
        } catch (Exception exception) {
            log.warn("Failed to load price presets from {}: {}", PRESETS_RESOURCE, exception.getMessage());
            return null;
        }
    }

    /**
     * Internal price entry matching the presets.json structure.
     */
    private record PriceEntry(
        double input,
        double output,
        double cacheRead,
        double cacheWrite
    ) {
    }
}
