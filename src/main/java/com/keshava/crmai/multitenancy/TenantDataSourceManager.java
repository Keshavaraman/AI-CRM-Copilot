package com.keshava.crmai.multitenancy;

import com.keshava.crmai.platform.entity.TenantDatasource;
import com.keshava.crmai.platform.repository.TenantDatasourceRepository;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantDataSourceManager {

    private final TenantDatasourceRepository tenantDatasourceRepository;
    private final TenantDataSourceRouter router;

    @EventListener(ApplicationReadyEvent.class)
    public void loadAllTenants() {
        log.info("Loading active tenant datasources...");
        tenantDatasourceRepository.findAllByActiveTrue().forEach(config -> {
            try {
                DataSource ds = buildDataSource(config);
                applyMigrations(ds);
                router.addTenant(config.getOrganization().getSubdomain(), ds);
            } catch (Exception e) {
                log.error("Failed to load tenant {}: {}", config.getOrganization().getSubdomain(), e.getMessage());
            }
        });
        log.info("Tenant datasources loaded.");
    }

    public void onboardTenant(TenantDatasource config) {
        DataSource ds = buildDataSource(config);
        applyMigrations(ds);
        router.addTenant(config.getOrganization().getSubdomain(), ds);
    }

    private DataSource buildDataSource(TenantDatasource config) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(config.getDbUrl());
        ds.setUsername(config.getDbUsername());
        ds.setPassword(config.getDbPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setPoolName("Tenant-" + config.getOrganization().getSubdomain());
        ds.setMaximumPoolSize(5);
        return ds;
    }

    private void applyMigrations(DataSource ds) {
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/tenant")
                .load()
                .migrate();
    }
}
