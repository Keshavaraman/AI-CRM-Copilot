package com.keshava.crmai.security.dto;

public record AuthResponse(
        String token,
        String email,
        String userId,
        String tenantId,
        long expiresIn
) {}
