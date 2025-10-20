package com.skytask.service;

import com.skytask.context.TenantContextHolder;
import com.skytask.entity.TenantEntity;
import com.skytask.repository.TenantRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final TenantRepository tenantRepository;
    private final Map<String, Long> tenantIdCache = new ConcurrentHashMap<>();
    private final Map<Long, String> tenantCodeCache = new ConcurrentHashMap<>();
    private final Map<Long, String> tenantNameCache = new ConcurrentHashMap<>();

    public Long currentTenantId() {
        String tenantCode = currentTenantCode();
        return tenantIdCache.computeIfAbsent(tenantCode, this::loadTenantId);
    }

    public String currentTenantCode() {
        String tenantCode = TenantContextHolder.getTenantCode();
        if (tenantCode == null) {
            throw new IllegalStateException("Tenant header is missing");
        }
        return tenantCode.toLowerCase();
    }

    public String resolveTenantCode(Long tenantId) {
        return tenantCodeCache.computeIfAbsent(tenantId, this::loadTenantCode);
    }

    public String resolveTenantName(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant id is required");
        }
        return tenantNameCache.computeIfAbsent(tenantId, this::loadTenantName);
    }

    private Long loadTenantId(String tenantCode) {
        TenantEntity tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantCode));
        tenantCodeCache.putIfAbsent(tenant.getId(), tenant.getCode());
        tenantNameCache.putIfAbsent(tenant.getId(), tenant.getName());
        return tenant.getId();
    }

    private String loadTenantCode(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        tenantIdCache.putIfAbsent(tenant.getCode(), tenant.getId());
        tenantNameCache.putIfAbsent(tenant.getId(), tenant.getName());
        return tenant.getCode();
    }

    private String loadTenantName(Long tenantId) {
        TenantEntity tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        tenantIdCache.putIfAbsent(tenant.getCode(), tenant.getId());
        tenantCodeCache.putIfAbsent(tenant.getId(), tenant.getCode());
        return tenant.getName();
    }
}
