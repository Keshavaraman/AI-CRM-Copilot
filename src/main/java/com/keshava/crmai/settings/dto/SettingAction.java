package com.keshava.crmai.settings.dto;

public record SettingAction(
        String key,
        String label,
        String method,
        String endpoint
) {}
