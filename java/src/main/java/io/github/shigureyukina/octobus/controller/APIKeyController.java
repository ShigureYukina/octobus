package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.APIKey;
import io.github.shigureyukina.octobus.domain.APIKeyService;
import io.github.shigureyukina.octobus.domain.CreateAPIKeyRequest;
import io.github.shigureyukina.octobus.domain.UpdateAPIKeyRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/v1/apikey")
class APIKeyController {

    private final APIKeyService apiKeyService;

    APIKeyController(APIKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/create")
    ApiResponse<APIKey> create(@RequestBody CreateAPIKeyRequest request) {
        return ApiResponse.success(apiKeyService.create(request));
    }

    @GetMapping("/list")
    ApiResponse<List<APIKey>> list() {
        return ApiResponse.success(apiKeyService.list());
    }

    @PostMapping("/update")
    ApiResponse<APIKey> update(@RequestBody UpdateAPIKeyRequest request) {
        return ApiResponse.success(apiKeyService.update(request));
    }

  @DeleteMapping("/delete/{id}")
  ApiResponse<Void> delete(@PathVariable int id) {
    apiKeyService.delete(id);
    return ApiResponse.success(null);
  }

  @GetMapping("/stats")
  ApiResponse<APIKey> stats(ServerWebExchange exchange) {
    String rawKey = extractApiKey(exchange);
    return ApiResponse.success(apiKeyService.findByApiKey(rawKey));
  }

  @GetMapping("/login")
  ApiResponse<APIKey> login(ServerWebExchange exchange) {
    String rawKey = extractApiKey(exchange);
    return ApiResponse.success(apiKeyService.login(rawKey));
  }

  private String extractApiKey(ServerWebExchange exchange) {
    var request = exchange.getRequest();
    var headers = request.getHeaders();
    String key = headers.getFirst("X-API-Key");
    if (key == null || key.isBlank()) {
      String auth = headers.getFirst("Authorization");
      if (auth != null && auth.startsWith("Bearer ")) {
        key = auth.substring(7);
      }
    }
    if (key == null || key.isBlank()) {
      throw new io.github.shigureyukina.octobus.common.ApiException(
          HttpStatus.UNAUTHORIZED, "Missing API key");
    }
    return key;
  }
}
