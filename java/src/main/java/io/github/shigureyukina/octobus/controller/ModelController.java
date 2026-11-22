package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.ChannelModel;
import io.github.shigureyukina.octobus.domain.ModelCreateRequest;
import io.github.shigureyukina.octobus.domain.ModelDeleteRequest;
import io.github.shigureyukina.octobus.domain.ModelInfo;
import io.github.shigureyukina.octobus.domain.ModelService;
import io.github.shigureyukina.octobus.domain.ModelUpdateRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model")
class ModelController {

    private final ModelService modelService;

    ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping("/list")
    ApiResponse<List<ModelInfo>> list() {
        return ApiResponse.success(modelService.listModels());
    }

    @PostMapping("/create")
    ApiResponse<ModelInfo> create(@RequestBody ModelCreateRequest request) {
        return ApiResponse.success(modelService.createModel(
            request.name(),
            request.input(),
            request.output(),
            request.cacheRead(),
            request.cacheWrite()
        ));
    }

    @PostMapping("/update")
    ApiResponse<ModelInfo> update(@RequestBody ModelUpdateRequest request) {
        return ApiResponse.success(modelService.updateModel(
            request.name(),
            request.input(),
            request.output(),
            request.cacheRead(),
            request.cacheWrite()
        ));
    }

    @PostMapping("/delete")
    ApiResponse<Void> delete(@RequestBody ModelDeleteRequest request) {
        modelService.deleteModel(request.name());
        return ApiResponse.success(null);
    }

    @GetMapping("/channel")
    ApiResponse<List<ChannelModel>> channel(@RequestParam(defaultValue = "0") int channelId) {
        return ApiResponse.success(modelService.listByChannel(channelId));
    }

    @PostMapping("/update-price")
    ApiResponse<Map<String, Object>> updatePrice() {
        int updated = modelService.updatePriceFromRemote();
        return ApiResponse.success(Map.of("updated", updated));
    }

    @GetMapping("/last-update-time")
    ApiResponse<Map<String, Object>> lastUpdateTime() {
        long time = modelService.getLastUpdateTime();
        return ApiResponse.success(Map.of("last_update_time", time));
    }
}
