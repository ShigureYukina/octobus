package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.task.LogFlushTask;
import io.github.shigureyukina.octobus.task.StatsFlushTask;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalController {

    private final StatsFlushTask statsFlushTask;
    private final LogFlushTask logFlushTask;

    public InternalController(StatsFlushTask statsFlushTask, LogFlushTask logFlushTask) {
        this.statsFlushTask = statsFlushTask;
        this.logFlushTask = logFlushTask;
    }

    @PostMapping("/api/internal/flush")
    ApiResponse<String> flush() {
        statsFlushTask.flushOnce();
        logFlushTask.flushOnce();
        return ApiResponse.success("flushed");
    }
}
