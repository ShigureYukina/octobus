package io.github.shigureyukina.octobus.controller;

import io.github.shigureyukina.octobus.common.ApiResponse;
import io.github.shigureyukina.octobus.domain.ChangePasswordRequest;
import io.github.shigureyukina.octobus.domain.ChangeUsernameRequest;
import io.github.shigureyukina.octobus.domain.UserLoginRequest;
import io.github.shigureyukina.octobus.domain.UserLoginResponse;
import io.github.shigureyukina.octobus.domain.UserService;
import io.github.shigureyukina.octobus.security.JwtClaims;
import io.github.shigureyukina.octobus.security.RequestAuthenticationFilter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping("/api/v1/user")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    ApiResponse<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @PostMapping("/logout")
    ApiResponse<String> logout(ServerWebExchange exchange) {
        return ApiResponse.success(userService.logout(requireClaims(exchange).subject()));
    }

    @PostMapping("/change-password")
    ApiResponse<String> changePassword(@Valid @RequestBody ChangePasswordRequest request, ServerWebExchange exchange) {
        return ApiResponse.success(userService.changePassword(requireClaims(exchange).subject(), request));
    }

    @PostMapping("/change-username")
    ApiResponse<String> changeUsername(@Valid @RequestBody ChangeUsernameRequest request, ServerWebExchange exchange) {
        return ApiResponse.success(userService.changeUsername(requireClaims(exchange).subject(), request));
    }

    @GetMapping("/status")
    ApiResponse<String> status() {
        return ApiResponse.success("ok");
    }

    private JwtClaims requireClaims(ServerWebExchange exchange) {
        Object claims = exchange.getAttribute(RequestAuthenticationFilter.JWT_CLAIMS_ATTRIBUTE);
        if (claims instanceof JwtClaims jwtClaims) {
            return jwtClaims;
        }
        throw new IllegalStateException("Missing JWT claims in authenticated request");
    }
}
