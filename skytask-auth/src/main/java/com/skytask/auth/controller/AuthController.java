package com.skytask.auth.controller;

import com.skytask.auth.dto.AuthLoginRequest;
import com.skytask.auth.dto.AuthProfileResponse;
import com.skytask.auth.dto.AuthTokenResponse;
import com.skytask.auth.security.JwtTokenProvider;
import com.skytask.auth.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String token = refreshToken;
        if ((token == null || token.isEmpty()) && body != null) {
            token = body.get("refreshToken");
        }
        return ResponseEntity.ok(authService.refresh(token));
    }

    @GetMapping("/profile")
    public ResponseEntity<AuthProfileResponse> profile(
            @RequestHeader(value = "Authorization") String authorization) {
        String token = extractToken(authorization);
        Jws<Claims> claims = tokenProvider.parseToken(token);
        Long userId = Long.parseLong(claims.getBody().getSubject());
        String tenantCode = claims.getBody().get("tenant", String.class);
        return ResponseEntity.ok(authService.loadProfile(userId, tenantCode));
    }

    /**
     * 登出接口
     * 删除用户的RefreshToken，使其无法刷新AccessToken
     */
    @PostMapping("/logout")
    public ResponseEntity<java.util.Map<String, String>> logout(
            @RequestHeader(value = "Authorization") String authorization) {
        String token = extractToken(authorization);
        Jws<Claims> claims = tokenProvider.parseToken(token);
        Long userId = Long.parseLong(claims.getBody().getSubject());

        authService.logout(userId);

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("message", "登出成功");
        return ResponseEntity.ok(response);
    }

    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
