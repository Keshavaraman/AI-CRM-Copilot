package com.keshava.crmai.settings.dto;

import java.util.List;

public record SettingsPage(
        String key,
        String title,
        String description,
        List<SettingAction> actions,
        List<String> fieldTypes
) {}
