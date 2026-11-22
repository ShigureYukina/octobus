package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.List;

/**
 * Orders group items according to a relay balancing strategy.
 */
public interface Balancer {

    int MODE_ROUND_ROBIN = 1;
    int MODE_RANDOM = 2;
    int MODE_FAILOVER = 3;
    int MODE_WEIGHTED = 4;

    List<GroupItem> order(List<GroupItem> items);

    static Balancer forMode(int mode) {
        return switch (mode) {
            case MODE_RANDOM -> new RandomBalancer();
            case MODE_FAILOVER -> new FailoverBalancer();
            case MODE_WEIGHTED -> new WeightedBalancer();
            case MODE_ROUND_ROBIN -> new RoundRobinBalancer();
            default -> new RoundRobinBalancer();
        };
    }
}
