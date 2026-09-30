package com.brokers.api.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByEmail(String email);

    boolean existsByEmail(String email);
}
