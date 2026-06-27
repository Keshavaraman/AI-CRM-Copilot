package com.keshava.crmai.module.dto;

import com.keshava.crmai.module.entity.DynamicField;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record FieldRequest(

        @NotBlank
        @Pattern(
                regexp = "^[a-z][a-z0-9_]*$",
                message = "apiName must be lowercase letters, digits, or underscores and start with a letter"
        )
        String apiName,

        @NotBlank
        String displayName,

        @NotNull
        DynamicField.FieldType fieldType,

        boolean required,

        String defaultValue
) {}
