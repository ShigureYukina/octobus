package io.github.shigureyukina.octobus.relay.balancer;

import io.github.shigureyukina.octobus.domain.GroupItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orders candidates by ascending priority.
 */
public class FailoverBalancer implements Balancer {

    @Override
    public List<GroupItem> order(List<GroupItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<GroupItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator.comparingInt(GroupItem::priority));
        return ordered;
    }
}
