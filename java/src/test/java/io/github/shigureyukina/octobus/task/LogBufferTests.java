package io.github.shigureyukina.octobus.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.domain.RelayLog;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogBufferTests {

    @Test
    void appendAndDrainAllPreservesBatchOrder() {
        LogBuffer buffer = new LogBuffer();
        RelayLog first = relayLog(1L, "model-a");
        RelayLog second = relayLog(2L, "model-b");

        buffer.append(first);
        buffer.append(second);

        assertThat(buffer.drainAll()).containsExactly(first, second);
        assertThat(buffer.drainAll()).isEmpty();
    }

    private RelayLog relayLog(long time, String model) {
        return new RelayLog(0L, time, model, "api-key", 1, "channel", model,
            1, 2, 0, 3, 0.1, true, "req", "resp", "", List.of(), 1, 0);
    }
}
