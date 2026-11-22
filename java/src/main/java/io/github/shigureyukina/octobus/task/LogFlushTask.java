package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.persistence.LogRepository;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically flushes buffered relay logs to the database.
 */
@Component
public class LogFlushTask {

    static final long FLUSH_INTERVAL_MS = 30_000L;

    private final LogBuffer logBuffer;
    private final LogRepository logRepository;

    public LogFlushTask(LogBuffer logBuffer, LogRepository logRepository) {
        this.logBuffer = logBuffer;
        this.logRepository = logRepository;
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void scheduledFlush() {
        flushOnce();
    }

    public void flushOnce() {
        List<io.github.shigureyukina.octobus.domain.RelayLog> logs = logBuffer.drainAll();
        if (logs.isEmpty()) {
            return;
        }
        logRepository.saveAll(logs);
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushOnce();
    }
}
