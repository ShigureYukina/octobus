package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Assigns a random weighted score to each candidate and sorts descending.
 */
public class WeightedBalancer implements Balancer {

    private final RandomGenerator random;

    public WeightedBalancer() {
        this(ThreadLocalRandom.current());
    }

    WeightedBalancer(RandomGenerator random) {
        this.random = random;
    }

    @Override
    public List<GroupItem> order(List<GroupItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        int totalWeight = items.stream()
            .mapToInt(item -> normalizeWeight(item.weight()))
            .sum();

        List<ScoredItem> scored = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            GroupItem item = items.get(index);
            double score = random.nextDouble()
                * normalizeWeight(item.weight())
                / (double) totalWeight;
            scored.add(new ScoredItem(item, score, index));
        }

        scored.sort(Comparator
            .comparingDouble(ScoredItem::score)
            .reversed()
            .thenComparingInt(ScoredItem::index));

        List<GroupItem> ordered = new ArrayList<>(items.size());
        for (ScoredItem item : scored) {
            ordered.add(item.item());
        }
        return ordered;
    }

    private static int normalizeWeight(int weight) {
        return weight > 0 ? weight : 1;
    }

    private record ScoredItem(GroupItem item, double score, int index) {
    }
}
