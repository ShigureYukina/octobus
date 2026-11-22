package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.StatsAPIKey;
import io.github.shigureyukina.octobus.domain.StatsDaily;
import io.github.shigureyukina.octobus.domain.StatsHourly;
import io.github.shigureyukina.octobus.domain.StatsService;
import io.github.shigureyukina.octobus.domain.StatsTotal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
class StatsController {

    private final StatsService statsService;

    StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/total")
    ApiResponse<StatsTotal> total() {
        return ApiResponse.success(statsService.total());
    }

    @GetMapping("/today")
    ApiResponse<StatsDaily> today() {
        return ApiResponse.success(statsService.today());
    }

    @GetMapping("/daily")
    ApiResponse<List<StatsDaily>> daily() {
        return ApiResponse.success(statsService.daily());
    }

  @GetMapping("/hourly")
  ApiResponse<List<StatsHourly>> hourly(
      @RequestParam(defaultValue = "24") int hours
  ) {
    return ApiResponse.success(statsService.hourly(hours));
  }

  @GetMapping("/apikey")
  ApiResponse<List<StatsAPIKey>> apikey() {
    return ApiResponse.success(statsService.apikeyStats());
  }
}
