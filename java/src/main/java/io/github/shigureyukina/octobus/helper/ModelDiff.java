package io.github.shigureyukina.octobus.helper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Case-insensitive multiset diff aligned with Go's utils/diff.Diff.
 */
public class ModelDiff {

    /**
     * Computes the multiset difference between old and new model lists.
     * Comparison is case-insensitive; the returned items preserve original casing
     * from their respective source list.
     */
    public static DiffResult diff(List<String> oldModels, List<String> newModels) {
        var oldMap = new LinkedHashMap<String, List<String>>();
        for (var m : oldModels) {
            if (m == null) continue;
            oldMap.computeIfAbsent(m.toLowerCase(), k -> new ArrayList<>()).add(m);
        }

        var deleted = new ArrayList<String>();
        var added = new ArrayList<String>();

        for (var m : newModels) {
            if (m == null) continue;
            var key = m.toLowerCase();
            var list = oldMap.get(key);
            if (list != null && !list.isEmpty()) {
                list.removeLast();
                if (list.isEmpty()) {
                    oldMap.remove(key);
                }
            } else {
                added.add(m);
            }
        }

        for (var entry : oldMap.entrySet()) {
            deleted.addAll(entry.getValue());
        }

        return new DiffResult(deleted, added);
    }

    private ModelDiff() {
    }
}
