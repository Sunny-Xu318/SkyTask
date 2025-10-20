package com.skytask.admin.config;

import feign.RequestInterceptor;
import javax.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignTenantHeaderConfig {

    private static final String[] FORWARDED_HEADERS = {
        "Authorization",
        "X-SkyTask-Tenant",
        "X-SkyTask-User",
        "X-SkyTask-UserId",
        "X-SkyTask-Roles",
        "X-SkyTask-Permissions"
    };

    @Bean
    public RequestInterceptor tenantHeaderRelayInterceptor() {
        return template -> {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (!(attributes instanceof ServletRequestAttributes)) {
                return;
            }
            HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
            if (request == null) {
                return;
            }
            for (String header : FORWARDED_HEADERS) {
                String value = request.getHeader(header);
                if (StringUtils.hasText(value)) {
                    template.header(header, value);
                }
            }
        };
    }
}
