package com.skytask.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthTokenResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private String tenantCode;
    private String tenantName;
    private String username;
    private String displayName;
    private java.util.List<String> roles;
    private java.util.List<String> permissions;
}
