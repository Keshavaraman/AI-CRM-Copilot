package com.keshava.crmai.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    // Called by MessageListenerAdapter — receives the raw JSON string from Redis pub/sub
    public void handleMessage(String message) {
        try {
            CacheInvalidationMessage msg = objectMapper.readValue(message, CacheInvalidationMessage.class);
            Cache cache = cacheManager.getCache(msg.cacheName());
            if (cache == null) return;

            if ("*".equals(msg.key())) {
                cache.clear();
                log.debug("Cleared Caffeine cache '{}' for tenant '{}' via Redis pub/sub", msg.cacheName(), msg.tenantId());
            } else {
                cache.evict(msg.tenantId() + ":" + msg.key());
                log.debug("Evicted '{}:{}' from Caffeine cache '{}' via Redis pub/sub", msg.tenantId(), msg.key(), msg.cacheName());
            }
        } catch (Exception e) {
            log.warn("Failed to process cache invalidation message: {}", e.getMessage());
        }
    }
}
