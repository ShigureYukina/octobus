package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.domain.ChannelSyncService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically syncs model lists from upstream providers for all auto-sync channels.
 *
 * <p>Relies on {@link TaskConfiguration} for {@code @EnableScheduling}.
 */
@Component
public class SyncModelsTask {

    private static final Logger log = LoggerFactory.getLogger(SyncModelsTask.class);

    private final ChannelSyncService channelSyncService;

    private volatile Instant lastSyncTime = Instant.now();

    public SyncModelsTask(ChannelSyncService channelSyncService) {
        this.channelSyncService = channelSyncService;
    }

    /**
     * Runs model sync every hour.
     */
    @Scheduled(fixedRate = 3_600_000)
    public void syncModels() {
        log.debug("Sync models task started");
        var start = Instant.now();
        try {
            channelSyncService.syncAllChannels();
        } catch (Exception e) {
            log.error("Sync models task failed: {}", e.getMessage());
        }
        lastSyncTime = Instant.now();
        log.debug("Sync models task finished, sync time: {}ms",
            java.time.Duration.between(start, lastSyncTime).toMillis());
    }

    /**
     * Returns the last time a full sync completed (successful or not).
     */
    public Instant getLastSyncTime() {
        return lastSyncTime;
    }
}
