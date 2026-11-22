package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Produces a full shuffle of the candidate list.
 */
public class RandomBalancer implements Balancer {

    private final RandomGenerator random;

    public RandomBalancer() {
        this(ThreadLocalRandom.current());
    }

    RandomBalancer(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public List<GroupItem> order(List<GroupItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<GroupItem> ordered = new ArrayList<>(items);
        for (int i = ordered.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            Collections.swap(ordered, i, swapIndex);
        }
        return ordered;
    }
}
