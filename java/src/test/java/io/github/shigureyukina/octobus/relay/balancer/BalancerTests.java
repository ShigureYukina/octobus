package io.github.shigureyukina.octobus.relay.balancer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class BalancerTests {

    @Test
    void roundRobinRotatesStartingPointAcrossCalls() {
        RoundRobinBalancer balancer = new RoundRobinBalancer(new AtomicLong(0));
        List<GroupItem> items = items();

        assertThat(channelIds(balancer.order(items))).containsExactly(2, 3, 1);
        assertThat(channelIds(balancer.order(items))).containsExactly(3, 1, 2);
        assertThat(channelIds(balancer.order(items))).containsExactly(1, 2, 3);
    }

    @Test
    void randomBalancerShufflesCandidates() {
        RandomBalancer balancer = new RandomBalancer(new FixedRandom(1, 0));

        assertThat(channelIds(balancer.order(items()))).containsExactly(3, 1, 2);
    }

    @Test
    void failoverBalancerSortsByAscendingPriority() {
        FailoverBalancer balancer = new FailoverBalancer();

        assertThat(channelIds(balancer.order(items()))).containsExactly(2, 3, 1);
    }

    @Test
    void weightedBalancerOrdersByWeightedScore() {
        WeightedBalancer balancer = new WeightedBalancer(new FixedRandom(0.9, 0.4, 0.8));

        assertThat(channelIds(balancer.order(items()))).containsExactly(3, 2, 1);
    }

    private static List<GroupItem> items() {
        return List.of(
            new GroupItem(1, 1, 1, "gpt-4", 30, 1),
            new GroupItem(2, 1, 2, "gpt-4", 10, 3),
            new GroupItem(3, 1, 3, "gpt-4", 20, 2)
        );
    }

    private static List<Integer> channelIds(List<GroupItem> items) {
        return items.stream().map(GroupItem::channelId).toList();
    }

    private static final class FixedRandom implements RandomGenerator {

        private final double[] doubles;
        private final int[] ints;
        private int doubleIndex;
        private int intIndex;

        private FixedRandom(int... ints) {
            this.ints = ints;
            this.doubles = new double[0];
        }

        private FixedRandom(double... doubles) {
            this.ints = new int[0];
            this.doubles = doubles;
        }

        @Override
        public int nextInt(int bound) {
            int value = ints[intIndex++];
            return Math.floorMod(value, bound);
        }

        @Override
        public double nextDouble() {
            return doubles[doubleIndex++];
        }

        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0;
        }

        @Override
        public double nextDouble(double bound) {
            return nextDouble() * bound;
        }

        @Override
        public int nextInt() {
            return 0;
        }

        @Override
        public long nextLong(long bound) {
            return 0;
        }

        @Override
        public double nextGaussian() {
            return 0;
        }
    }
}
