package com.skytask.config;

import com.skytask.context.TenantContextHolder;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String HEADER_TENANT = "X-SkyTask-Tenant";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String tenantCode = request.getHeader(HEADER_TENANT);
            if (!StringUtils.hasText(tenantCode)) {
                // ⚠️ 开发环境：如果没有租户header，使用默认租户
                // TODO: 生产环境应该返回400错误
                tenantCode = "default";
                logger.debug("Development mode: Using default tenant");
                
                /* 生产环境使用以下代码：
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":400,\"message\":\"Missing tenant header\"}");
                return;
                */
            }
            TenantContextHolder.setTenantCode(tenantCode.toLowerCase());
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
