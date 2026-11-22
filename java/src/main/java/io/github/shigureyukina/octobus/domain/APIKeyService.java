package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.APIKeyRepository;
import java.security.SecureRandom;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class APIKeyService {

    private static final String KEY_PREFIX = "sk-octopus-";
    private static final int KEY_RANDOM_LENGTH = 48;
    private static final String KEY_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final APIKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom;

    public APIKeyService(APIKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.secureRandom = new SecureRandom();
    }

    public APIKey create(CreateAPIKeyRequest request) {
        String rawKey = generateAPIKey();
        APIKey key = new APIKey(
            0,
            request.name(),
            rawKey,
            request.enabled() != null && request.enabled(),
            request.expireAt(),
            request.maxCost(),
            request.supportedModels()
        );
        return apiKeyRepository.save(key);
    }

    public APIKey update(UpdateAPIKeyRequest request) {
        APIKey existing = apiKeyRepository.findById(request.id())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "API key not found"));

        APIKey updated = new APIKey(
            request.id(),
            request.name() != null ? request.name() : existing.name(),
            existing.apiKey(),
            request.enabled() != null ? request.enabled() : existing.enabled(),
            request.expireAt() != null ? request.expireAt() : existing.expireAt(),
            request.maxCost() != null ? request.maxCost() : existing.maxCost(),
            request.supportedModels() != null ? request.supportedModels() : existing.supportedModels()
        );
        apiKeyRepository.update(updated);
        return updated;
    }

    public List<APIKey> list() {
        return apiKeyRepository.findAll();
    }

  public void delete(int id) {
    if (!apiKeyRepository.deleteById(id)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "API key not found");
    }
  }

  public APIKey findByApiKey(String apiKey) {
    return apiKeyRepository.findByApiKey(apiKey)
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
  }

  public APIKey login(String apiKey) {
    APIKey key = findByApiKey(apiKey);
    if (!key.enabled()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "API key is disabled");
    }
    if (key.expireAt() != null && key.expireAt() > 0 && key.expireAt() < java.time.Instant.now().getEpochSecond()) {
      throw new ApiException(HttpStatus.FORBIDDEN, "API key has expired");
    }
    return key;
  }

  private String generateAPIKey() {
        StringBuilder sb = new StringBuilder(KEY_PREFIX.length() + KEY_RANDOM_LENGTH);
        sb.append(KEY_PREFIX);
        for (int i = 0; i < KEY_RANDOM_LENGTH; i++) {
            sb.append(KEY_CHARS.charAt(secureRandom.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }
}
