package com.skytask.gateway.filter;

import com.skytask.gateway.security.JwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import java.net.URI;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST = List.of(
            "/auth/**", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JwtVerifier jwtVerifier;

    public GatewayAuthFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        URI requestUri = exchange.getRequest().getURI();
        String path = requestUri.getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing bearer token");
        }
        String token = authorization.substring(7);
        try {
            Jws<Claims> claims = jwtVerifier.parse(token);
            Claims body = claims.getBody();
            if (!"access".equals(body.get("type"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token 类型错误");
            }
            String username = body.get("username", String.class);
            String tenant = body.get("tenant", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) body.getOrDefault("roles", List.of());
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) body.getOrDefault("permissions", List.of());

            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header("X-SkyTask-User", username != null ? username : "")
                    .header("X-SkyTask-UserId", body.getSubject())
                    .header("X-SkyTask-Tenant", tenant != null ? tenant : "")
                    .header("X-SkyTask-Roles", String.join(",", roles))
                    .header("X-SkyTask-Permissions", String.join(",", permissions))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException | ResponseStatusException ex) {
            return unauthorized(exchange, "Invalid token");
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    public int getOrder() {
        return -10;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        String response = String.format("{\"code\":401,\"message\":\"%s\"}", message);
        return exchange.getResponse().writeWith(Mono.just(
                exchange.getResponse().bufferFactory().wrap(response.getBytes())));
    }
}
