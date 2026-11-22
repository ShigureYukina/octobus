package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.persistence.StatsRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flushes accumulated stats to the database.
 */
@Component
public class StatsFlushTask {

    static final long FLUSH_INTERVAL_MS = 30_000L;

    private final StatsAccumulator statsAccumulator;
    private final StatsRepository statsRepository;

    public StatsFlushTask(StatsAccumulator statsAccumulator, StatsRepository statsRepository) {
        this.statsAccumulator = statsAccumulator;
        this.statsRepository = statsRepository;
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void scheduledFlush() {
        flushOnce();
    }

    public void flushOnce() {
        StatsAccumulator.Snapshot snapshot = statsAccumulator.drain();
        if (snapshot.isEmpty()) {
            return;
        }

        if (snapshot.total() != null && !snapshot.total().equals(io.github.shigureyukina.octobus.domain.StatsMetrics.zero())) {
            statsRepository.updateTotal(snapshot.total());
        }
        snapshot.daily().forEach(statsRepository::updateDaily);
        snapshot.hourly().forEach((bucket, metrics) -> statsRepository.updateHourly(bucket.hour(), bucket.date(), metrics));
        snapshot.apiKeys().forEach(statsRepository::updateAPIKey);
        snapshot.channels().forEach(statsRepository::updateChannel);
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushOnce();
    }
}
