package io.github.shigureyukina.octobus.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.shigureyukina.octobus.domain.StatsMetrics;
import io.github.shigureyukina.octobus.persistence.StatsRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class StatsFlushTaskTests {

    @Test
    void scheduledFlushUsesThirtySecondFixedRate() throws NoSuchMethodException {
        Scheduled scheduled = StatsFlushTask.class
            .getMethod("scheduledFlush")
            .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedRate()).isEqualTo(30_000L);
    }

    @Test
    void flushOncePersistsMergedBuckets() {
        StatsAccumulator accumulator = new StatsAccumulator();
        StatsRepository repository = mock(StatsRepository.class);
        StatsFlushTask task = new StatsFlushTask(accumulator, repository);

        accumulator.record(
            LocalDateTime.of(2026, 6, 1, 10, 15),
            new StatsMetrics(3, 4, 0.1, 0.2, 5, 1, 0, 6),
            7,
            9
        );

        task.flushOnce();

        StatsMetrics expected = new StatsMetrics(3, 4, 0.1, 0.2, 5, 1, 0, 6);
        verify(repository, times(1)).updateTotal(expected);
        verify(repository, times(1)).updateDaily("2026-06-01", expected);
        verify(repository, times(1)).updateHourly(10, "2026-06-01", expected);
        verify(repository, times(1)).updateAPIKey(7, expected);
        verify(repository, times(1)).updateChannel(9, expected);

        task.flushOnce();
        verify(repository, times(1)).updateTotal(expected);
    }
}
