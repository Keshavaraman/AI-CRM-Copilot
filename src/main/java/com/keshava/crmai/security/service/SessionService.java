package com.keshava.crmai.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.keshava.crmai.security.dto.SessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String SESSION_PREFIX   = "session:";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtService jwtService;

    // Called on login — persists session metadata so any server can inspect active sessions.
    public void store(String token, SessionInfo info) {
        try {
            long ttlMs = jwtService.extractExpiration(token).getTime() - System.currentTimeMillis();
            if (ttlMs <= 0) return;

            String key = sessionKey(info.tenantId(), info.userId(), jwtService.extractJti(token));
            stringRedisTemplate.opsForValue().set(key, MAPPER.writeValueAsString(info), ttlMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Could not store session for user {}: {}", info.userId(), e.getMessage());
        }
    }

    // Called on logout — blacklists the jti so every server rejects this token immediately.
    public void revoke(String token) {
        try {
            String jti     = jwtService.extractJti(token);
            String tenantId = jwtService.extractTenantId(token);
            String userId  = jwtService.extractUserId(token);
            long ttlMs = jwtService.extractExpiration(token).getTime() - Instant.now().toEpochMilli();

            if (ttlMs > 0) {
                stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", ttlMs, TimeUnit.MILLISECONDS);
            }
            stringRedisTemplate.delete(sessionKey(tenantId, userId, jti));
        } catch (Exception e) {
            log.warn("Could not revoke session: {}", e.getMessage());
        }
    }

    // Called by JwtAuthFilter on every authenticated request.
    public boolean isRevoked(String token) {
        try {
            String jti = jwtService.extractJti(token);
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            return false;
        }
    }

    private String sessionKey(String tenantId, String userId, String jti) {
        return SESSION_PREFIX + tenantId + ":" + userId + ":" + jti;
    }
}
