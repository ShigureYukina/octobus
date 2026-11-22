package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.helper.ChannelAutoGroup;
import io.github.shigureyukina.octobus.helper.ModelDiff;
import io.github.shigureyukina.octobus.helper.ProviderModelFetcher;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.GroupRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the model sync flow for channels:
 * <ol>
 *   <li>Fetch upstream model list via {@link ProviderModelFetcher}
 *   <li>Diff against the current channel model list
 *   <li>Update channel model if changed
 *   <li>Clean up orphaned group_items for deleted models
 *   <li>Re-run auto-group assignment
 * </ol>
 */
@Service
public class ChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChannelSyncService.class);

    private final ChannelRepository channelRepository;
    private final GroupRepository groupRepository;
    private final ProviderModelFetcher providerModelFetcher;
    private final ChannelAutoGroup channelAutoGroup;

    public ChannelSyncService(ChannelRepository channelRepository,
                              GroupRepository groupRepository,
                              ProviderModelFetcher providerModelFetcher,
                              ChannelAutoGroup channelAutoGroup) {
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.providerModelFetcher = providerModelFetcher;
        this.channelAutoGroup = channelAutoGroup;
    }

    /**
     * Syncs models for all channels that have autoSync enabled.
     * Failed channels are logged and skipped; one failure does not block others.
     */
    public void syncAllChannels() {
        var channels = channelRepository.findAll();
        for (var channel : channels) {
            if (channel.autoSync() == null || !channel.autoSync()) {
                continue;
            }
            try {
                syncChannel(channel);
            } catch (Exception e) {
                log.warn("Failed to sync channel {} (id={}): {}", channel.name(), channel.id(), e.getMessage());
            }
        }
    }

    /**
     * Syncs models for a specific channel by its id.
     *
     * @param channelId the channel primary key
     * @throws ApiException if the channel is not found
     */
    public void syncChannel(int channelId) {
        var channel = channelRepository.findById(channelId);
        if (channel == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        syncChannel(channel);
    }

    /**
     * Fetches and returns the upstream model list for a channel without persisting changes.
     *
     * @param channelId the channel primary key
     * @return upstream model list
     * @throws ApiException if the channel is not found or fetch fails
     */
    public List<String> fetchModels(int channelId) {
        var channel = channelRepository.findById(channelId);
        if (channel == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        return providerModelFetcher.fetchModels(channel);
    }

    /**
     * Fetches and returns the upstream model list for a full channel object (no DB lookup).
     */
    public List<String> fetchModels(Channel channel) {
        return providerModelFetcher.fetchModels(channel);
    }

    // ---- internal ----

    private void syncChannel(Channel channel) {
        List<String> newModels;
        try {
            newModels = providerModelFetcher.fetchModels(channel);
        } catch (Exception e) {
            log.warn("Failed to fetch models for channel {} (id={}): {}", channel.name(), channel.id(), e.getMessage());
            return;
        }

        var oldModelNames = parseModelList(channel.model());
        var diff = ModelDiff.diff(oldModelNames, newModels);

        if (diff.deleted().isEmpty() && diff.added().isEmpty()) {
            log.debug("No model changes for channel {} (id={})", channel.name(), channel.id());
            return;
        }

        // Update channel model
        var newModelStr = String.join(",", newModels);
        channelRepository.update(
            channel.id(), null, null, null, null,
            newModelStr, null, null, null, null, null, null, null, null
        );
        log.info("Updated channel {} (id={}) models: {} deleted, {} added",
            channel.name(), channel.id(), diff.deleted().size(), diff.added().size());

        // Clean up orphaned group_items for deleted models
        if (!diff.deleted().isEmpty()) {
            groupRepository.deleteItemsByChannelAndModels(channel.id(), diff.deleted());
            log.info("Cleaned up {} group_items for deleted models on channel {}",
                diff.deleted().size(), channel.name());
        }

        // Re-run auto-group
        if (!newModels.isEmpty()) {
            channelAutoGroup.autoGroup(channel);
        }
    }

    private List<String> parseModelList(String modelStr) {
        if (modelStr == null || modelStr.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var part : modelStr.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
