package io.github.shigureyukina.octobus.helper;

import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.Group;
import io.github.shigureyukina.octobus.domain.GroupIDAndLLMName;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Assigns channel models to matching groups based on the channel's auto-group mode.
 *
 * <p>Auto-group modes:
 * <ul>
 *   <li>0 (NONE) — no grouping
 *   <li>1 (FUZZY) — model name contains group name (case-insensitive)
 *   <li>2 (EXACT) — model name equals group name (case-insensitive)
 *   <li>3 (REGEX) — model name matches the group's matchRegex
 * </ul>
 */
@Component
public class ChannelAutoGroup {

    private final GroupRepository groupRepository;

    public ChannelAutoGroup(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    /**
     * Assigns models from the channel to matching groups.
     * Does nothing if autoGroup is 0 (NONE).
     *
     * @param channel the channel whose models should be grouped
     */
    public void autoGroup(Channel channel) {
        if (channel == null) return;

        var mode = channel.autoGroup();
        if (mode == null || mode == 0) return;

        var allGroups = groupRepository.findAll();
        if (allGroups.isEmpty()) return;

        var modelNames = collectModelNames(channel);
        if (modelNames.isEmpty()) return;

        // Remove all existing items for this channel first, then re-insert.
        // This keeps auto-group deterministic and avoids duplicate-key errors.
        groupRepository.deleteItemsByChannel(channel.id());

        for (var group : allGroups) {
            var matched = matchModels(group, mode, modelNames);
            if (!matched.isEmpty()) {
                var items = matched.stream()
                    .map(m -> new GroupIDAndLLMName(channel.id(), m))
                    .toList();
                groupRepository.batchAddItems(group.id(), items);
            }
        }
    }

    private List<String> collectModelNames(Channel channel) {
        var names = new ArrayList<String>();
        var model = channel.model();
        if (model != null && !model.isBlank()) {
            for (var part : model.split(",")) {
                var trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        var customModel = channel.customModel();
        if (customModel != null && !customModel.isBlank()) {
            for (var part : customModel.split(",")) {
                var trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    names.add(trimmed);
                }
            }
        }
        return names;
    }

    private List<String> matchModels(Group group, int mode, List<String> modelNames) {
        var matched = new ArrayList<String>();
        var seen = new HashSet<String>();

        for (var modelName : modelNames) {
            if (seen.contains(modelName)) continue;

            boolean matches = switch (mode) {
                case 1 -> modelName.toLowerCase().contains(group.name().toLowerCase());
                case 2 -> modelName.equalsIgnoreCase(group.name());
                case 3 -> matchesRegex(modelName, group.matchRegex());
                default -> false;
            };

            if (matches) {
                matched.add(modelName);
                seen.add(modelName);
            }
        }

        return matched;
    }

    private boolean matchesRegex(String modelName, String regex) {
        if (regex == null || regex.isBlank()) return false;
        try {
            return Pattern.compile(regex).matcher(modelName).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
