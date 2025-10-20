package com.skytask.auth.service;

import com.skytask.auth.dto.AuthLoginRequest;
import com.skytask.auth.dto.AuthProfileResponse;
import com.skytask.auth.dto.AuthTokenResponse;
import com.skytask.auth.entity.PermissionEntity;
import com.skytask.auth.entity.RolePermissionEntity;
import com.skytask.auth.entity.TenantEntity;
import com.skytask.auth.entity.UserEntity;
import com.skytask.auth.entity.UserRoleEntity;
import com.skytask.auth.entity.UserTokenEntity;
import com.skytask.auth.repository.RolePermissionRepository;
import com.skytask.auth.repository.TenantRepository;
import com.skytask.auth.repository.UserRepository;
import com.skytask.auth.repository.UserRoleRepository;
import com.skytask.auth.repository.UserTokenRepository;
import com.skytask.auth.security.JwtTokenProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserTokenRepository userTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Transactional
    public AuthTokenResponse login(AuthLoginRequest request) {
        String tenantCode = request.getTenantCode().toLowerCase(Locale.ROOT);
        TenantEntity tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "租户不存在"));

        if (!StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码不能为空");
        }

        UserEntity user = userRepository.findByTenant_CodeAndUsername(tenantCode, request.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "用户已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }

        List<UserRoleEntity> userRoles = userRoleRepository.findByUser_Id(user.getId());
        Set<String> roleNames = userRoles.stream()
                .map(role -> role.getRole().getName())
                .collect(Collectors.toSet());
        List<Long> roleIds = userRoles.stream()
                .map(role -> role.getRole().getId())
                .collect(Collectors.toList());
        Set<String> permissions = rolePermissionRepository.findByRole_IdIn(roleIds)
                .stream()
                .map(RolePermissionEntity::getPermission)
                .map(PermissionEntity::getName)
                .collect(Collectors.toSet());

        userTokenRepository.deleteByUserId(user.getId());
        userTokenRepository.deleteExpired(Instant.now());

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                tenant.getId(),
                tenant.getCode(),
                List.copyOf(roleNames),
                List.copyOf(permissions));
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), tenant.getCode());

        UserTokenEntity tokenEntity = new UserTokenEntity();
        tokenEntity.setUser(user);
        tokenEntity.setRefreshToken(refreshToken);
        tokenEntity.setExpiresAt(Instant.now().plus(tokenProvider.getRefreshTokenTtlHours(), ChronoUnit.HOURS));
        tokenEntity.setCreatedAt(Instant.now());
        userTokenRepository.save(tokenEntity);

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenTtlMinutes() * 60)
                .tenantCode(tenant.getCode())
                .tenantName(tenant.getName())
                .username(user.getUsername())
                .displayName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername())
                .roles(List.copyOf(roleNames))
                .permissions(List.copyOf(permissions))
                .build();
    }

    @Transactional
    public AuthTokenResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少刷新令牌");
        }
        UserTokenEntity tokenEntity = userTokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌不存在"));
        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            userTokenRepository.delete(tokenEntity);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌已过期");
        }
        UserEntity user = tokenEntity.getUser();
        TenantEntity tenant = user.getTenant();

        List<UserRoleEntity> userRoles = userRoleRepository.findByUser_Id(user.getId());
        Set<String> roleNames = userRoles.stream()
                .map(role -> role.getRole().getName())
                .collect(Collectors.toSet());
        List<Long> roleIds = userRoles.stream()
                .map(role -> role.getRole().getId())
                .collect(Collectors.toList());
        Set<String> permissions = rolePermissionRepository.findByRole_IdIn(roleIds)
                .stream()
                .map(RolePermissionEntity::getPermission)
                .map(PermissionEntity::getName)
                .collect(Collectors.toSet());

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                tenant.getId(),
                tenant.getCode(),
                List.copyOf(roleNames),
                List.copyOf(permissions));

        return AuthTokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenTtlMinutes() * 60)
                .tenantCode(tenant.getCode())
                .tenantName(tenant.getName())
                .username(user.getUsername())
                .displayName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername())
                .roles(List.copyOf(roleNames))
                .permissions(List.copyOf(permissions))
                .build();
    }

    @Transactional
    public AuthProfileResponse loadProfile(Long userId, String tenantCode) {
        UserEntity user = userRepository.findByIdAndTenant_Code(userId, tenantCode)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        TenantEntity tenant = user.getTenant();

        List<UserRoleEntity> userRoles = userRoleRepository.findByUser_Id(user.getId());
        Set<String> roleNames = userRoles.stream()
                .map(role -> role.getRole().getName())
                .collect(Collectors.toSet());
        List<Long> roleIds = userRoles.stream()
                .map(role -> role.getRole().getId())
                .collect(Collectors.toList());
        Set<String> permissions = rolePermissionRepository.findByRole_IdIn(roleIds)
                .stream()
                .map(RolePermissionEntity::getPermission)
                .map(PermissionEntity::getName)
                .collect(Collectors.toSet());

        return AuthProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName() : user.getUsername())
                .roles(List.copyOf(roleNames))
                .permissions(List.copyOf(permissions))
                .tenantCode(tenant.getCode())
                .tenantName(tenant.getName())
                .build();
    }

    /**
     * 登出
     * 删除用户的所有RefreshToken
     */
    @Transactional
    public void logout(Long userId) {
        int deleted = userTokenRepository.deleteByUserId(userId);
        // 可以添加日志记录
    }
}
