package com.keshava.crmai.platform.repository;

import com.keshava.crmai.platform.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    boolean existsByPlatformUserIdAndOrganizationIdAndActiveTrue(UUID platformUserId, UUID organizationId);

    List<OrganizationMember> findByPlatformUserIdAndActiveTrue(UUID platformUserId);

    Optional<OrganizationMember> findByPlatformUserIdAndOrganizationSubdomain(UUID platformUserId, String subdomain);
}
