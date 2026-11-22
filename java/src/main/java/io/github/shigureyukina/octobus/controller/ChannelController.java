package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.Channel;
import io.github.shigureyukina.octobus.domain.ChannelCreateRequest;
import io.github.shigureyukina.octobus.domain.ChannelService;
import io.github.shigureyukina.octobus.domain.ChannelSyncService;
import io.github.shigureyukina.octobus.domain.ChannelUpdateRequest;
import io.github.shigureyukina.octobus.task.SyncModelsTask;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/channel")
class ChannelController {

    private final ChannelService channelService;
    private final ChannelSyncService channelSyncService;
    private final SyncModelsTask syncModelsTask;

    ChannelController(ChannelService channelService,
                      ChannelSyncService channelSyncService,
                      SyncModelsTask syncModelsTask) {
        this.channelService = channelService;
        this.channelSyncService = channelSyncService;
        this.syncModelsTask = syncModelsTask;
    }

    @GetMapping("/list")
    ApiResponse<List<Channel>> list() {
        return ApiResponse.success(channelService.list());
    }

    @PostMapping("/create")
    ApiResponse<Channel> create(@Valid @RequestBody ChannelCreateRequest req) {
        return ApiResponse.success(channelService.create(req));
    }

    @PostMapping("/update")
    ApiResponse<Channel> update(@RequestBody ChannelUpdateRequest req) {
        return ApiResponse.success(channelService.update(req));
    }

    @PostMapping("/enable")
    ApiResponse<Void> enable(@RequestBody Map<String, Object> body) {
        int id = ((Number) body.get("id")).intValue();
        boolean enabled = (Boolean) body.get("enabled");
        channelService.enable(id, enabled);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/delete/{id}")
    ApiResponse<Void> delete(@PathVariable int id) {
        channelService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/fetch-models")
    Mono<ApiResponse<List<String>>> fetchModels(@RequestBody Map<String, Object> body) {
        var idObj = body.get("id");
        if (idObj == null) {
            return Mono.error(new ApiException(HttpStatus.BAD_REQUEST, "Missing required field: id"));
        }
        int id = ((Number) idObj).intValue();
        return Mono.fromCallable(() ->
            ApiResponse.success(channelSyncService.fetchModels(id))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Go-compatible fetch-model contract: accept a full channel payload and fetch models
     * without persisting channel changes.
     */
    @PostMapping("/fetch-model")
    Mono<ApiResponse<List<String>>> fetchModel(@RequestBody Channel request) {
        return Mono.fromCallable(() ->
            ApiResponse.success(channelSyncService.fetchModels(request))
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/sync")
    ApiResponse<Void> syncChannels() {
        syncModelsTask.syncModels();
        return ApiResponse.success(null);
    }

    @GetMapping("/last-sync-time")
    ApiResponse<Map<String, Object>> lastSyncTime() {
        var time = syncModelsTask.getLastSyncTime();
        return ApiResponse.success(Map.of(
            "last_sync_time", time != null ? time.toString() : null
        ));
    }
}
