package com.keshava.crmai.multitenancy;

import com.keshava.crmai.common.exception.TenantNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TenantDataSourceRouter extends AbstractRoutingDataSource {

    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceRouter() {
        setTargetDataSources(new HashMap<>());
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // Return null during startup/non-request contexts; AbstractRoutingDataSource handles null gracefully.
        // Request-time null is caught in determineTargetDataSource().
        return TenantContext.getCurrentTenant();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String key = (String) determineCurrentLookupKey();
        if (key == null) {
            throw new TenantNotFoundException("No tenant context — set X-Tenant-Id header");
        }
        DataSource ds = tenantDataSources.get(key);
        if (ds == null) {
            throw new TenantNotFoundException(key);
        }
        return ds;
    }

    public synchronized void addTenant(String tenantId, DataSource dataSource) {
        tenantDataSources.put(tenantId, dataSource);
        Map<Object, Object> targets = new HashMap<>(tenantDataSources);
        setTargetDataSources(targets);
        afterPropertiesSet();
        log.info("Registered tenant datasource: {}", tenantId);
    }

    public boolean hasTenant(String tenantId) {
        return tenantDataSources.containsKey(tenantId);
    }
}
