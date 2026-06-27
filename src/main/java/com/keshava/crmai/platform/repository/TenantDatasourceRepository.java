package com.keshava.crmai.platform.repository;

import com.keshava.crmai.platform.entity.TenantDatasource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantDatasourceRepository extends JpaRepository<TenantDatasource, UUID> {

    List<TenantDatasource> findAllByActiveTrue();

    Optional<TenantDatasource> findByOrganizationSubdomain(String subdomain);
}
