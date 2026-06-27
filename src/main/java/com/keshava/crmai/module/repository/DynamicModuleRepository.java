package com.keshava.crmai.module.repository;

import com.keshava.crmai.module.entity.DynamicModule;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DynamicModuleRepository extends JpaRepository<DynamicModule, UUID> {

    @Cacheable("moduleMetadata")
    Optional<DynamicModule> findByApiName(String apiName);

    List<DynamicModule> findAllByActiveTrue();

    boolean existsByApiName(String apiName);
}
