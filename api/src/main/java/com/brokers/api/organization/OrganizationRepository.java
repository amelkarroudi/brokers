package com.brokers.api.organization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    boolean existsBySlug(String slug);
}
