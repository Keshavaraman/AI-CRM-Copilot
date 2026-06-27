package com.keshava.crmai.module.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.config.cache.CacheInvalidationPublisher;
import com.keshava.crmai.module.dto.FieldRequest;
import com.keshava.crmai.module.entity.DynamicField;
import com.keshava.crmai.module.entity.DynamicModule;
import com.keshava.crmai.module.repository.DynamicFieldRepository;
import com.keshava.crmai.multitenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DynamicFieldService {

    private final DynamicFieldRepository fieldRepository;
    private final DynamicModuleService moduleService;
    private final CacheInvalidationPublisher cachePublisher;

    @Qualifier("tenantJdbcTemplate")
    private final JdbcTemplate tenantJdbcTemplate;

    @Transactional(transactionManager = "tenantTransactionManager")
    public DynamicField addField(String moduleApiName, FieldRequest request) {
        DynamicModule module = moduleService.getModule(moduleApiName);

        if (fieldRepository.existsByModuleIdAndApiName(module.getId(), request.apiName())) {
            throw new AppException(
                    "Field '" + request.apiName() + "' already exists on module '" + moduleApiName + "'",
                    HttpStatus.CONFLICT);
        }

        DynamicField field = new DynamicField();
        field.setModule(module);
        field.setApiName(request.apiName());
        field.setDisplayName(request.displayName());
        field.setFieldType(request.fieldType());
        field.setRequired(request.required());
        field.setDefaultValue(request.defaultValue());
        field.setActive(true);

        DynamicField saved = fieldRepository.save(field);

        // Add physical column to the tenant table
        String sql = "ALTER TABLE " + module.getTableName() +
                " ADD COLUMN IF NOT EXISTS " + request.apiName() +
                " " + toSqlType(request.fieldType());
        tenantJdbcTemplate.execute(sql);

        cachePublisher.publishClear("fieldMetadata", TenantContext.getCurrentTenant());
        return saved;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<DynamicField> listFields(String moduleApiName) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        return fieldRepository.findByModuleIdAndActiveTrue(module.getId());
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteField(String moduleApiName, String fieldApiName) {
        DynamicModule module = moduleService.getModule(moduleApiName);
        DynamicField field = fieldRepository
                .findByModuleIdAndApiName(module.getId(), fieldApiName)
                .orElseThrow(() -> new AppException(
                        "Field '" + fieldApiName + "' not found on module '" + moduleApiName + "'",
                        HttpStatus.NOT_FOUND));

        field.setActive(false);
        fieldRepository.save(field);
        // Physical column is NOT dropped to preserve existing data.
        cachePublisher.publishClear("fieldMetadata", TenantContext.getCurrentTenant());
    }

    private String toSqlType(DynamicField.FieldType type) {
        return switch (type) {
            case TEXT, MULTI_PICKLIST -> "TEXT";
            case NUMBER -> "NUMERIC(20,4)";
            case DATE -> "DATE";
            case DATETIME -> "TIMESTAMP";
            case PICKLIST, EMAIL -> "VARCHAR(255)";
            case PHONE -> "VARCHAR(50)";
            case URL -> "VARCHAR(500)";
            case LOOKUP -> "UUID";
            case BOOLEAN -> "BOOLEAN";
        };
    }
}
