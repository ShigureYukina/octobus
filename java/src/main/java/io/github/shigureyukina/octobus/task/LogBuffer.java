package io.github.shigureyukina.octobus.task;

import io.github.shigureyukina.octobus.domain.RelayLog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Component;

/**
 * In-memory queue for relay logs before batch persistence.
 */
@Component
public class LogBuffer {

    private final ConcurrentLinkedQueue<RelayLog> queue = new ConcurrentLinkedQueue<>();

    public void append(RelayLog log) {
        if (log != null) {
            queue.add(log);
        }
    }

    public List<RelayLog> drainAll() {
        List<RelayLog> drained = new ArrayList<>();
        RelayLog next;
        while ((next = queue.poll()) != null) {
            drained.add(next);
        }
        return drained;
    }
}
