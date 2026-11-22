package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.persistence.SettingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SettingService {

    public static final String PROXY_URL = "proxy_url";
    public static final String STATS_SAVE_INTERVAL = "stats_save_interval";
    public static final String MODEL_INFO_UPDATE_INTERVAL = "model_info_update_interval";
    public static final String SYNC_LLM_INTERVAL = "sync_llm_interval";
    public static final String RELAY_LOG_KEEP_PERIOD = "relay_log_keep_period";
    public static final String RELAY_LOG_KEEP_ENABLED = "relay_log_keep_enabled";
    public static final String CORS_ALLOW_ORIGINS = "cors_allow_origins";
    public static final String CIRCUIT_BREAKER_THRESHOLD = "circuit_breaker_threshold";
    public static final String CIRCUIT_BREAKER_COOLDOWN = "circuit_breaker_cooldown";
    public static final String CIRCUIT_BREAKER_MAX_COOLDOWN = "circuit_breaker_max_cooldown";

    private static final Set<String> VALID_KEYS = Set.of(
        PROXY_URL, STATS_SAVE_INTERVAL, MODEL_INFO_UPDATE_INTERVAL, SYNC_LLM_INTERVAL,
        RELAY_LOG_KEEP_PERIOD, RELAY_LOG_KEEP_ENABLED, CORS_ALLOW_ORIGINS,
        CIRCUIT_BREAKER_THRESHOLD, CIRCUIT_BREAKER_COOLDOWN, CIRCUIT_BREAKER_MAX_COOLDOWN
    );

    private static final Map<String, String> DEFAULT_SETTINGS = buildDefaults();

    private final SettingRepository settingRepository;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public void initializeDefaults() {
        if (settingRepository.count() > 0) {
            return;
        }
        for (var entry : DEFAULT_SETTINGS.entrySet()) {
            settingRepository.upsert(entry.getKey(), entry.getValue());
        }
    }

    public List<Setting> list() {
        return settingRepository.findAll();
    }

  public Setting set(String key, String value) {
    if (!VALID_KEYS.contains(key)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown setting key: " + key);
    }
    settingRepository.upsert(key, value);
    return new Setting(key, value);
  }

  public Map<String, Object> exportAll() {
    Map<String, Object> dump = new LinkedHashMap<>();
    dump.put("version", 1);
    dump.put("exported_at", java.time.Instant.now().toString());
    dump.put("settings", settingRepository.findAll());
    return dump;
  }

  public void importDump(Map<String, Object> dump) {
    @SuppressWarnings("unchecked")
    var settings = (List<LinkedHashMap<String, String>>) dump.get("settings");
    if (settings == null || settings.isEmpty()) {
      return;
    }
    settingRepository.deleteAll();
    for (var entry : settings) {
      settingRepository.upsert(entry.get("key"), entry.get("value"));
    }
  }

  private static Map<String, String> buildDefaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(PROXY_URL, "");
        map.put(STATS_SAVE_INTERVAL, "10");
        map.put(CORS_ALLOW_ORIGINS, "");
        map.put(MODEL_INFO_UPDATE_INTERVAL, "24");
        map.put(SYNC_LLM_INTERVAL, "24");
        map.put(RELAY_LOG_KEEP_PERIOD, "7");
        map.put(RELAY_LOG_KEEP_ENABLED, "true");
        map.put(CIRCUIT_BREAKER_THRESHOLD, "5");
        map.put(CIRCUIT_BREAKER_COOLDOWN, "60");
        map.put(CIRCUIT_BREAKER_MAX_COOLDOWN, "600");
        return Map.copyOf(map);
    }
}
