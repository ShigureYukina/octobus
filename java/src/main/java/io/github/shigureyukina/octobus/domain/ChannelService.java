package io.github.shigureyukina.octobus.domain;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.helper.ChannelAutoGroup;
import io.github.shigureyukina.octobus.persistence.ChannelRepository;
import io.github.shigureyukina.octobus.persistence.StatsRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelAutoGroup channelAutoGroup;
    private final StatsRepository statsRepository;

    public ChannelService(ChannelRepository channelRepository, ChannelAutoGroup channelAutoGroup,
                          StatsRepository statsRepository) {
        this.channelRepository = channelRepository;
        this.channelAutoGroup = channelAutoGroup;
        this.statsRepository = statsRepository;
    }

    public List<Channel> list() {
        var channels = channelRepository.findAll();
        var statsMap = statsRepository.findChannelStatsMap();
        return channels.stream()
            .map(ch -> new Channel(
                ch.id(), ch.name(), ch.type(), ch.enabled(), ch.baseUrls(), ch.keys(),
                ch.model(), ch.customModel(), ch.proxy(), ch.autoSync(), ch.autoGroup(),
                ch.customHeader(), ch.paramOverride(), ch.channelProxy(), ch.matchRegex(),
                statsMap.get(ch.id())
            ))
            .toList();
    }

    public Channel getById(int id) {
        if (!channelRepository.exists(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        var ch = channelRepository.findById(id);
        var statsMap = statsRepository.findChannelStatsMap();
        return new Channel(
            ch.id(), ch.name(), ch.type(), ch.enabled(), ch.baseUrls(), ch.keys(),
            ch.model(), ch.customModel(), ch.proxy(), ch.autoSync(), ch.autoGroup(),
            ch.customHeader(), ch.paramOverride(), ch.channelProxy(), ch.matchRegex(),
            statsMap.get(ch.id())
        );
    }

    public Channel create(ChannelCreateRequest req) {
        var baseUrlsJson = channelRepository.serializeBaseUrls(req.baseUrls());
        var customHeaderJson = channelRepository.serializeCustomHeaders(req.customHeader());
        int id = channelRepository.create(
            req.name(),
            req.type() != null ? req.type() : "",
            req.enabled() != null ? req.enabled() : true,
            baseUrlsJson,
            req.model() != null ? req.model() : "",
            req.customModel() != null ? req.customModel() : "",
            req.proxy() != null ? req.proxy() : false,
            req.autoSync() != null ? req.autoSync() : false,
            req.autoGroup() != null ? req.autoGroup() : 0,
            customHeaderJson,
            req.paramOverride(),
            req.channelProxy(),
            req.matchRegex()
        );
        if (req.keys() != null) {
            for (var key : req.keys()) {
                channelRepository.addKey(id, key.enabled() != null ? key.enabled() : true, key.channelKey(), key.remark() != null ? key.remark() : "");
            }
        }
        var channel = channelRepository.findById(id);
        channelAutoGroup.autoGroup(channel);
        return channel;
    }

    public Channel update(ChannelUpdateRequest req) {
        if (!channelRepository.exists(req.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        var baseUrlsJson = req.baseUrls() != null ? channelRepository.serializeBaseUrls(req.baseUrls()) : null;
        var customHeaderJson = req.customHeader() != null ? channelRepository.serializeCustomHeaders(req.customHeader()) : null;
        channelRepository.update(
            req.id(), req.name(), req.type(), req.enabled(), baseUrlsJson,
            req.model(), req.customModel(), req.proxy(), req.autoSync(),
            req.autoGroup(), customHeaderJson, req.paramOverride(),
            req.channelProxy(), req.matchRegex()
        );
        if (req.keysToAdd() != null) {
            for (var k : req.keysToAdd()) {
                channelRepository.addKey(req.id(), k.enabled() != null ? k.enabled() : true, k.channelKey(), k.remark() != null ? k.remark() : "");
            }
        }
        if (req.keysToUpdate() != null) {
            for (var k : req.keysToUpdate()) {
                channelRepository.updateKey(k.id(), k.enabled(), k.channelKey(), k.remark());
            }
        }
        if (req.keysToDelete() != null) {
            for (var keyId : req.keysToDelete()) {
                channelRepository.deleteKey(keyId);
            }
        }
        var channel = channelRepository.findById(req.id());
        channelAutoGroup.autoGroup(channel);
        return channel;
    }

    public void enable(int id, boolean enabled) {
        if (!channelRepository.exists(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        channelRepository.setEnabled(id, enabled);
    }

    public void delete(int id) {
        if (!channelRepository.exists(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Channel not found");
        }
        channelRepository.delete(id);
    }
}
