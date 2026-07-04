package com.keshava.crmai.settings.dto;

import java.util.List;

public record SettingsResponse(
        int count,
        List<SettingsPage> pages
) {}
