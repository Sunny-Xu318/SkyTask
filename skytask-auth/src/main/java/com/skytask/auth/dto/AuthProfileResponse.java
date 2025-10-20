package com.skytask.auth.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthProfileResponse {

    private Long userId;
    private String username;
    private String displayName;
    private List<String> roles;
    private List<String> permissions;
    private String tenantCode;
    private String tenantName;
}
