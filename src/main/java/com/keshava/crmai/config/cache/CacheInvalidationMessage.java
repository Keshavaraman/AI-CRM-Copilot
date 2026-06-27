package com.keshava.crmai.config.cache;

public record CacheInvalidationMessage(String cacheName, String tenantId, String key) {
    // key = "*" means clear the entire cache for that tenant
}
