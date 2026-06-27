package com.keshava.crmai.multitenancy;

public final class TenantContext {

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static String getCurrentTenant() {
        return TENANT_ID.get();
    }

    public static void setCurrentTenant(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
