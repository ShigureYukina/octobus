package io.github.shigureyukina.octobus.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.shigureyukina.octobus.domain.RelayLog;
import io.github.shigureyukina.octobus.persistence.LogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class LogFlushTaskTests {

    @Test
    void scheduledFlushUsesThirtySecondFixedRate() throws NoSuchMethodException {
        Scheduled scheduled = LogFlushTask.class
            .getMethod("scheduledFlush")
            .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRate()).isEqualTo(30_000L);
    }

    @Test
    void flushOncePersistsBufferedLogsAsSingleBatch() {
        LogBuffer buffer = new LogBuffer();
        LogRepository repository = mock(LogRepository.class);
        LogFlushTask task = new LogFlushTask(buffer, repository);

        RelayLog first = relayLog(1L, "model-a");
        RelayLog second = relayLog(2L, "model-b");
        buffer.append(first);
        buffer.append(second);

        task.flushOnce();

        verify(repository, times(1)).saveAll(List.of(first, second));

        task.flushOnce();
        verify(repository, times(1)).saveAll(List.of(first, second));
    }

    private RelayLog relayLog(long time, String model) {
        return new RelayLog(0L, time, model, "api-key", 1, "channel", model,
            1, 2, 0, 3, 0.1, true, "req", "resp", "", List.of(), 1, 0);
    }
}
