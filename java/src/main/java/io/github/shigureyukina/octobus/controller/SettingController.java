package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiException;
import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.BackupService;
import io.github.shigureyukina.octobus.domain.DBDump;
import io.github.shigureyukina.octobus.domain.DBImportResult;
import io.github.shigureyukina.octobus.domain.Setting;
import io.github.shigureyukina.octobus.domain.SettingService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/setting")
class SettingController {

    private final SettingService settingService;
    private final BackupService backupService;

    SettingController(SettingService settingService, BackupService backupService) {
        this.settingService = settingService;
        this.backupService = backupService;
    }

    @GetMapping("/list")
    ApiResponse<List<Setting>> list() {
        return ApiResponse.success(settingService.list());
    }

    @PostMapping("/set")
    ApiResponse<Setting> set(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        String value = body.get("value");
        return ApiResponse.success(settingService.set(key, value));
    }

    @GetMapping("/export")
    ResponseEntity<ApiResponse<DBDump>> export_(
        @RequestParam(value = "include_logs", defaultValue = "false") boolean includeLogs,
        @RequestParam(value = "include_stats", defaultValue = "false") boolean includeStats
    ) {
        DBDump dump = backupService.exportAll(includeLogs, includeStats);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "octopus-export-" + timestamp + ".json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(ApiResponse.success(dump));
    }

    /**
     * Full-database import via multipart form-data (file field).
     * This is the primary path used by the frontend.
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Mono<ApiResponse<DBImportResult>> importMultipart(@RequestPart("file") FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
            .map(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                return bytes;
            })
            .publishOn(Schedulers.boundedElastic())
            .map(bytes -> {
                DBDump dump = backupService.parseDump(bytes);
                return backupService.importDump(dump);
            })
            .map(ApiResponse::success)
            .onErrorResume(ApiException.class, Mono::error)
            .onErrorResume(e -> Mono.error(
                new ApiException(HttpStatus.BAD_REQUEST,
                    "Failed to process upload: " + e.getMessage())));
    }

    /**
     * Full-database import via raw JSON body.
     * Also supports the legacy setting-only format for backward compatibility.
     */
    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<?> importJson(@RequestBody Map<String, Object> body) {
        // Try full dump import first
        DBDump dump = null;
        try {
            byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsBytes(body);
            dump = backupService.parseDump(bytes);
        } catch (Exception ignored) {
            // Not parseable as DBDump, fall through to legacy
        }

        if (dump != null && !isDumpEmpty(dump)) {
            return ApiResponse.success(backupService.importDump(dump));
        }

        // Legacy setting-only import
        settingService.importDump(body);
        return ApiResponse.success(null);
    }

    private boolean isDumpEmpty(DBDump dump) {
        return dump.version() == 0
            && (dump.channels() == null || dump.channels().isEmpty())
            && (dump.channelKeys() == null || dump.channelKeys().isEmpty())
            && (dump.groups() == null || dump.groups().isEmpty())
            && (dump.groupItems() == null || dump.groupItems().isEmpty())
            && (dump.settings() == null || dump.settings().isEmpty())
            && (dump.apiKeys() == null || dump.apiKeys().isEmpty())
            && (dump.llmInfos() == null || dump.llmInfos().isEmpty())
            && (dump.relayLogs() == null || dump.relayLogs().isEmpty())
            && (dump.statsDaily() == null || dump.statsDaily().isEmpty())
            && (dump.statsHourly() == null || dump.statsHourly().isEmpty())
            && (dump.statsTotal() == null || dump.statsTotal().isEmpty())
            && (dump.statsChannel() == null || dump.statsChannel().isEmpty())
            && (dump.statsApiKey() == null || dump.statsApiKey().isEmpty());
    }
}
