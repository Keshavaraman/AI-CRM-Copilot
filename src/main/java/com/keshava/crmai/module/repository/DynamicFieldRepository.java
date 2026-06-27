package com.keshava.crmai.module.repository;

import com.keshava.crmai.module.entity.DynamicField;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DynamicFieldRepository extends JpaRepository<DynamicField, UUID> {

    @Cacheable("fieldMetadata")
    List<DynamicField> findByModuleId(UUID moduleId);

    List<DynamicField> findByModuleIdAndActiveTrue(UUID moduleId);

    boolean existsByModuleIdAndApiName(UUID moduleId, String apiName);

    Optional<DynamicField> findByModuleIdAndApiName(UUID moduleId, String apiName);
}
