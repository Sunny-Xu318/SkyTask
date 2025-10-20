package com.skytask.auth.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthProfile {

    private String username;
    private String displayName;
    private List<String> roles;
    private List<String> permissions;
    private String tenant;
}
