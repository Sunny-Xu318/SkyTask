package com.skytask.admin.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantAuthenticationFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-SkyTask-Tenant";
    private static final String USER_HEADER = "X-SkyTask-User";
    private static final String USER_ID_HEADER = "X-SkyTask-UserId";
    private static final String PERMISSIONS_HEADER = "X-SkyTask-Permissions";

    private final List<RequestMatcher> ignoredMatchers = Arrays.asList(
            new AntPathRequestMatcher("/actuator/**"),
            new AntPathRequestMatcher("/auth/**"));

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return ignoredMatchers.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String tenantCode = request.getHeader(TENANT_HEADER);
        String username = request.getHeader(USER_HEADER);
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String permissionsHeader = request.getHeader(PERMISSIONS_HEADER);

        if (!StringUtils.hasText(authorization)
                || !StringUtils.hasText(tenantCode)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(userIdHeader)
                || !StringUtils.hasText(permissionsHeader)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing authentication headers");
            return;
        }

        List<SimpleGrantedAuthority> authorities = Arrays.stream(permissionsHeader.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        Long userId = null;
        try {
            userId = Long.valueOf(userIdHeader);
        } catch (NumberFormatException ignored) {
        }

        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user id header");
            return;
        }

        TenantAuthenticationToken authentication =
                new TenantAuthenticationToken(userId, username, tenantCode.toLowerCase(), authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
