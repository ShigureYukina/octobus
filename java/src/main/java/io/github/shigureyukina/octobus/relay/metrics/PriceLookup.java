package io.github.shigureyukina.octobus.relay.metrics;

import io.github.shigureyukina.octobus.domain.ModelInfo;
import io.github.shigureyukina.octobus.persistence.ModelRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Looks up model prices from the database and caches them in memory.
 *
 * <p>Matches the Go-side {@code price.GetLLMPrice()} semantics: returns
 * a price entry or {@link ModelPrice#zero()} when the model is unknown.
 */
@Component
public class PriceLookup {

    private final ModelRepository modelRepository;
    private final Object reloadLock = new Object();
    private volatile Map<String, ModelPrice> priceCache = Map.of();

    public PriceLookup(ModelRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    /**
     * Load the cache only after the application is ready so Flyway migrations and
     * bootstrap seeders have finished creating/populating the model table.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpAfterStartup() {
        refresh();
    }

    /**
     * Returns the price for the given model name, or zero-price if unknown.
     */
    public ModelPrice priceFor(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ModelPrice.zero();
        }
        ModelPrice price = priceCache.get(modelName);
        return price != null ? price : ModelPrice.zero();
    }

    /**
     * Reloads the price cache from the database. Called on startup and
     * can be called on demand.
     */
    public void refresh() {
        synchronized (reloadLock) {
            Map<String, ModelPrice> fresh = new ConcurrentHashMap<>();
            for (ModelInfo info : modelRepository.findAll()) {
                if (info.name() != null && !info.name().isBlank()) {
                    fresh.put(info.name(), new ModelPrice(
                        info.input(),
                        info.output(),
                        info.cacheRead(),
                        info.cacheWrite()
                    ));
                }
            }
            priceCache = Map.copyOf(fresh);
        }
    }
}
