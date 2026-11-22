package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.LogFilter;
import io.github.shigureyukina.octobus.domain.LogListResponse;
import io.github.shigureyukina.octobus.domain.LogService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/log")
class LogController {

    private final LogService logService;

    LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/list")
    ApiResponse<LogListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "start_time", required = false) Long startTime,
            @RequestParam(name = "end_time", required = false) Long endTime,
            @RequestParam(name = "model_name", required = false) String modelName,
            @RequestParam(name = "channel_name", required = false) String channelName,
            @RequestParam(required = false) String status
    ) {
        var filter = new LogFilter(page, pageSize, startTime, endTime, modelName, channelName, status);
        return ApiResponse.success(logService.list(filter));
    }

    @DeleteMapping("/clear")
    ApiResponse<Void> clear() {
        logService.clear();
        return ApiResponse.success(null);
    }

    @GetMapping("/stream")
    Flux<ServerSentEvent<String>> stream(@RequestParam String token) {
        return logService.stream(token);
    }

    @PostMapping("/stream-token")
    ApiResponse<Map<String, String>> createStreamToken() {
        String token = logService.createStreamToken();
        return ApiResponse.success(Map.of("token", token));
    }
}
