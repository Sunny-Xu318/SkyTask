package com.skytask.context;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestContextAdapter {

    private static final String HEADER_USER = "X-SkyTask-User";
    private static final String HEADER_PERMISSIONS = "X-SkyTask-Permissions";

    public String currentUser() {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader(HEADER_USER) : null;
    }

    public boolean hasPermission(String permission) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String raw = request.getHeader(HEADER_PERMISSIONS);
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        Set<String> permissions = new HashSet<>();
        for (String item : raw.split(",")) {
            if (StringUtils.hasText(item)) {
                permissions.add(item.trim());
            }
        }
        return permissions.contains(permission);
    }

    public Set<String> permissions() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Collections.emptySet();
        }
        String raw = request.getHeader(HEADER_PERMISSIONS);
        if (!StringUtils.hasText(raw)) {
            return Collections.emptySet();
        }
        Set<String> permissions = new HashSet<>();
        for (String item : raw.split(",")) {
            if (StringUtils.hasText(item)) {
                permissions.add(item.trim());
            }
        }
        return permissions;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }
}
