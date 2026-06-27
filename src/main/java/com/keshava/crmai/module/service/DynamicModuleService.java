package com.keshava.crmai.module.service;

import com.keshava.crmai.common.exception.AppException;
import com.keshava.crmai.config.cache.CacheInvalidationPublisher;
import com.keshava.crmai.module.dto.ModuleRequest;
import com.keshava.crmai.module.entity.DynamicModule;
import com.keshava.crmai.module.repository.DynamicModuleRepository;
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
public class DynamicModuleService {

    private final DynamicModuleRepository moduleRepository;
    private final CacheInvalidationPublisher cachePublisher;

    @Qualifier("tenantJdbcTemplate")
    private final JdbcTemplate tenantJdbcTemplate;

    @Transactional(transactionManager = "tenantTransactionManager")
    public DynamicModule createModule(ModuleRequest request) {
        if (moduleRepository.existsByApiName(request.apiName())) {
            throw new AppException("Module with apiName '" + request.apiName() + "' already exists", HttpStatus.CONFLICT);
        }

        String tableName = "cm_" + request.apiName();

        DynamicModule module = new DynamicModule();
        module.setApiName(request.apiName());
        module.setDisplayName(request.displayName());
        module.setType(DynamicModule.ModuleType.CUSTOM);
        module.setTableName(tableName);
        module.setActive(true);

        DynamicModule saved = moduleRepository.save(module);

        // Create physical table in tenant DB (PostgreSQL supports transactional DDL)
        tenantJdbcTemplate.execute(buildCreateTableSql(tableName));

        cachePublisher.publishClear("moduleMetadata", TenantContext.getCurrentTenant());
        return saved;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<DynamicModule> listModules() {
        return moduleRepository.findAllByActiveTrue();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public DynamicModule getModule(String apiName) {
        return moduleRepository.findByApiName(apiName)
                .filter(DynamicModule::isActive)
                .orElseThrow(() -> new AppException("Module '" + apiName + "' not found", HttpStatus.NOT_FOUND));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteModule(String apiName) {
        DynamicModule module = getModule(apiName);
        if (module.getType() == DynamicModule.ModuleType.SYSTEM) {
            throw new AppException("System modules cannot be deleted", HttpStatus.FORBIDDEN);
        }
        module.setActive(false);
        moduleRepository.save(module);
        // NOTE: Physical table is intentionally NOT dropped to preserve data history.
        // Re-activate by setting active=true if needed.
        cachePublisher.publishClear("moduleMetadata", TenantContext.getCurrentTenant());
    }

    private String buildCreateTableSql(String tableName) {
        return "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id UUID PRIMARY KEY DEFAULT gen_random_uuid(), " +
                "created_at TIMESTAMP NOT NULL DEFAULT now(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT now(), " +
                "created_by VARCHAR(255), " +
                "updated_by VARCHAR(255)" +
                ")";
    }
}
