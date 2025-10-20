package com.skytask.context;

public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_CODE = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantCode(String tenantCode) {
        TENANT_CODE.set(tenantCode);
    }

    public static String getTenantCode() {
        return TENANT_CODE.get();
    }

    public static void clear() {
        TENANT_CODE.remove();
    }
}
