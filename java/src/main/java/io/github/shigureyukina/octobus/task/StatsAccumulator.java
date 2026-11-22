package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.domain.StatsMetrics;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-memory accumulator for stats writes before periodic flush.
 */
@Component
public class StatsAccumulator {

    private final Object lock = new Object();
    private MutableState current = new MutableState();

    public void record(LocalDateTime recordedAt, StatsMetrics delta, int apiKeyId, int channelId) {
        if (recordedAt == null || delta == null) {
            return;
        }

        synchronized (lock) {
            current.total = merge(current.total, delta);

            String date = recordedAt.toLocalDate().toString();
            current.daily.merge(date, delta, StatsAccumulator::merge);

            HourBucket hourBucket = new HourBucket(recordedAt.getHour(), date);
            current.hourly.merge(hourBucket, delta, StatsAccumulator::merge);

            if (apiKeyId > 0) {
                current.apiKeys.merge(apiKeyId, delta, StatsAccumulator::merge);
            }
            if (channelId > 0) {
                current.channels.merge(channelId, delta, StatsAccumulator::merge);
            }
        }
    }

    public Snapshot drain() {
        synchronized (lock) {
            MutableState drained = current;
            current = new MutableState();
            return drained.toSnapshot();
        }
    }

    static StatsMetrics merge(StatsMetrics left, StatsMetrics right) {
        StatsMetrics a = left == null ? StatsMetrics.zero() : left;
        StatsMetrics b = right == null ? StatsMetrics.zero() : right;
        return new StatsMetrics(
            a.inputToken() + b.inputToken(),
            a.outputToken() + b.outputToken(),
            a.inputCost() + b.inputCost(),
            a.outputCost() + b.outputCost(),
            a.waitTime() + b.waitTime(),
            a.requestSuccess() + b.requestSuccess(),
            a.requestFailed() + b.requestFailed(),
            a.cacheHitToken() + b.cacheHitToken()
        );
    }

    public record HourBucket(int hour, String date) {
    }

    public record Snapshot(
        StatsMetrics total,
        Map<String, StatsMetrics> daily,
        Map<HourBucket, StatsMetrics> hourly,
        Map<Integer, StatsMetrics> apiKeys,
        Map<Integer, StatsMetrics> channels
    ) {
        public boolean isEmpty() {
            boolean totalEmpty = total == null || total.equals(StatsMetrics.zero());
            return totalEmpty
                && daily.isEmpty()
                && hourly.isEmpty()
                && apiKeys.isEmpty()
                && channels.isEmpty();
        }
    }

    private static final class MutableState {
        private StatsMetrics total = StatsMetrics.zero();
        private final Map<String, StatsMetrics> daily = new LinkedHashMap<>();
        private final Map<HourBucket, StatsMetrics> hourly = new LinkedHashMap<>();
        private final Map<Integer, StatsMetrics> apiKeys = new LinkedHashMap<>();
        private final Map<Integer, StatsMetrics> channels = new LinkedHashMap<>();

        private Snapshot toSnapshot() {
            return new Snapshot(
                total,
                Map.copyOf(daily),
                Map.copyOf(hourly),
                Map.copyOf(apiKeys),
                Map.copyOf(channels)
            );
        }
    }
}
