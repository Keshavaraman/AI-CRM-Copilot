package com.keshava.crmai.platform.repository;

import com.keshava.crmai.platform.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);
}
