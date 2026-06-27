package com.keshava.crmai.security.dto;

import java.time.Instant;

public record SessionInfo(
        String userId,
        String email,
        String tenantId,
        String ipAddress,
        String userAgent,
        Instant loginAt,
        Instant expiresAt
) {}
