package com.keshava.crmai.module.dto;

import java.util.List;
import java.util.Map;

public record RecordPage(
        List<Map<String, Object>> content,
        long totalElements,
        int page,
        int size,
        int totalPages
) {}
