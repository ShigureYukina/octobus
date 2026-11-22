package io.github.shigureyukina.octobus.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.github.shigureyukina.octobus.domain.StatsMetrics;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class StatsAccumulatorTests {

    @Test
    void recordMergesSameBuckets() {
        StatsAccumulator accumulator = new StatsAccumulator();
        LocalDateTime recordedAt = LocalDateTime.of(2026, 6, 1, 10, 15, 0);

        accumulator.record(recordedAt, new StatsMetrics(1, 2, 0.1, 0.2, 3, 1, 0, 4), 7, 9);
        accumulator.record(recordedAt, new StatsMetrics(5, 6, 0.3, 0.4, 7, 0, 1, 8), 7, 9);

        StatsAccumulator.Snapshot snapshot = accumulator.drain();
        assertMetrics(snapshot.total(), 6, 8, 0.4, 0.6, 10, 1, 1, 12);
        assertMetrics(snapshot.daily().get("2026-06-01"), 6, 8, 0.4, 0.6, 10, 1, 1, 12);
        assertMetrics(snapshot.hourly().get(new StatsAccumulator.HourBucket(10, "2026-06-01")), 6, 8, 0.4, 0.6, 10, 1, 1, 12);
        assertMetrics(snapshot.apiKeys().get(7), 6, 8, 0.4, 0.6, 10, 1, 1, 12);
        assertMetrics(snapshot.channels().get(9), 6, 8, 0.4, 0.6, 10, 1, 1, 12);
    }

    @Test
    void recordBucketsByRecordedTimeAndDrainResetsState() {
        StatsAccumulator accumulator = new StatsAccumulator();

        accumulator.record(LocalDateTime.of(2026, 6, 1, 23, 59, 59), new StatsMetrics(1, 0, 0, 0, 0, 1, 0, 0), 0, 0);
        accumulator.record(LocalDateTime.of(2026, 6, 2, 0, 0, 0), new StatsMetrics(2, 0, 0, 0, 0, 1, 0, 0), 0, 0);

        StatsAccumulator.Snapshot snapshot = accumulator.drain();
        assertThat(snapshot.daily()).containsOnlyKeys("2026-06-01", "2026-06-02");
        assertThat(snapshot.hourly()).containsOnlyKeys(
            new StatsAccumulator.HourBucket(23, "2026-06-01"),
            new StatsAccumulator.HourBucket(0, "2026-06-02")
        );

        StatsAccumulator.Snapshot emptySnapshot = accumulator.drain();
        assertThat(emptySnapshot.isEmpty()).isTrue();
    }

    private void assertMetrics(
        StatsMetrics actual,
        long inputToken,
        long outputToken,
        double inputCost,
        double outputCost,
        long waitTime,
        long requestSuccess,
        long requestFailed,
        long cacheHitToken
    ) {
        assertThat(actual).isNotNull();
        assertThat(actual.inputToken()).isEqualTo(inputToken);
        assertThat(actual.outputToken()).isEqualTo(outputToken);
        assertThat(actual.inputCost()).isCloseTo(inputCost, offset(1e-10));
        assertThat(actual.outputCost()).isCloseTo(outputCost, offset(1e-10));
        assertThat(actual.waitTime()).isEqualTo(waitTime);
        assertThat(actual.requestSuccess()).isEqualTo(requestSuccess);
        assertThat(actual.requestFailed()).isEqualTo(requestFailed);
        assertThat(actual.cacheHitToken()).isEqualTo(cacheHitToken);
    }
}
