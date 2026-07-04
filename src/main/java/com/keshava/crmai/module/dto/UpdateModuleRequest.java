package com.keshava.crmai.module.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateModuleRequest(@NotBlank String displayName) {}
