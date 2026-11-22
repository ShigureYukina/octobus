package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.ModelRepository;
import io.github.shigureyukina.octobus.relay.metrics.PriceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service for model-related queries.
 */
@Service
public class ModelService {

    private final ModelRepository modelRepository;
    private final ChannelRepository channelRepository;
    private final PriceLookup priceLookup;

    public ModelService(ModelRepository modelRepository, ChannelRepository channelRepository, PriceLookup priceLookup) {
        this.modelRepository = modelRepository;
        this.channelRepository = channelRepository;
        this.priceLookup = priceLookup;
    }

    /**
     * Returns all models.
     */
    public List<ModelInfo> listModels() {
        return modelRepository.findAll();
    }

    public ModelInfo createModel(String name, double input, double output, double cacheRead, double cacheWrite) {
        String normalizedName = normalizeName(name);
        if (modelRepository.existsByName(normalizedName)) {
            throw new ApiException(HttpStatus.CONFLICT, "Model already exists");
        }

        modelRepository.create(normalizedName, input, output, cacheRead, cacheWrite);
        priceLookup.refresh();
        return modelRepository.findByName(normalizedName)
            .orElseThrow(() -> new IllegalStateException("Created model not found"));
    }

    public ModelInfo updateModel(String name, Double input, Double output, Double cacheRead, Double cacheWrite) {
        String normalizedName = normalizeName(name);
        if (!modelRepository.existsByName(normalizedName)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Model not found");
        }

        modelRepository.update(normalizedName, input, output, cacheRead, cacheWrite);
        priceLookup.refresh();
        return modelRepository.findByName(normalizedName)
            .orElseThrow(() -> new IllegalStateException("Updated model not found"));
    }

    public void deleteModel(String name) {
        String normalizedName = normalizeName(name);
        if (!modelRepository.existsByName(normalizedName)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Model not found");
        }

        modelRepository.deleteByName(normalizedName);
        priceLookup.refresh();
    }

    public List<String> listModelNames() {
        return modelRepository.findAllNames();
    }

    public List<ChannelModel> listByChannel(int channelId) {
        var channels = channelRepository.findAll();
        var result = new ArrayList<ChannelModel>();

        for (var ch : channels) {
            if (channelId > 0 && ch.id() != channelId) continue;

            var names = parseModelField(ch.model());
            if (ch.customModel() != null && !ch.customModel().isBlank()) {
                names.addAll(parseModelField(ch.customModel()));
            }

            // De-duplicate while preserving order
            var seen = new java.util.LinkedHashSet<String>();
            for (var name : names) {
                if (seen.add(name)) {
                    result.add(new ChannelModel(name, ch.enabled(), ch.id(), ch.name()));
                }
            }
        }

        return result;
    }

    private List<String> parseModelField(String field) {
        var result = new ArrayList<String>();
        if (field == null || field.isBlank()) return result;
        for (var part : field.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public int updatePriceFromRemote() {
        // MVP: return 0 - remote price update will be added in Phase 3
        return 0;
    }

    public long getLastUpdateTime() {
        return 0;
    }

    public List<String> listModelNamesForChannel(int channelId) {
        return modelRepository.findAllNames();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Model name is required");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
