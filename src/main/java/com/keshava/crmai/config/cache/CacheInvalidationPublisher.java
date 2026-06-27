package com.keshava.crmai.config.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    public static final String CHANNEL = "cache:invalidation";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishEvict(String cacheName, String tenantId, String key) {
        publish(new CacheInvalidationMessage(cacheName, tenantId, key));
    }

    public void publishClear(String cacheName, String tenantId) {
        publish(new CacheInvalidationMessage(cacheName, tenantId, "*"));
    }

    private void publish(CacheInvalidationMessage message) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            log.warn("Failed to publish cache invalidation for {}/{}: {}", message.cacheName(), message.tenantId(), e.getMessage());
        }
    }
}
