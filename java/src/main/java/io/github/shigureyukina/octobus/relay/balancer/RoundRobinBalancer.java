package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rotates the candidate starting point on each call.
 */
public class RoundRobinBalancer implements Balancer {

    private static final AtomicLong SHARED_COUNTER = new AtomicLong();

    private final AtomicLong counter;

    public RoundRobinBalancer() {
        this(SHARED_COUNTER);
    }

    RoundRobinBalancer(AtomicLong counter) {
        this.counter = counter;
    }

    @Override
    public List<GroupItem> order(List<GroupItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<GroupItem> ordered = new ArrayList<>(items.size());
        int size = items.size();
        int start = (int) Math.floorMod(counter.incrementAndGet(), size);
        for (int i = 0; i < size; i++) {
            ordered.add(items.get((start + i) % size));
        }
        return ordered;
    }
}
