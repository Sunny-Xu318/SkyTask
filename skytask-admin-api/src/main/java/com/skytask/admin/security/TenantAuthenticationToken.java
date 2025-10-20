package com.skytask.admin.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class TenantAuthenticationToken extends AbstractAuthenticationToken {

    private final Long userId;
    private final String username;
    private final String tenantCode;

    public TenantAuthenticationToken(
            Long userId, String username, String tenantCode, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.userId = userId;
        this.username = username;
        this.tenantCode = tenantCode;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTenantCode() {
        return tenantCode;
    }
}
