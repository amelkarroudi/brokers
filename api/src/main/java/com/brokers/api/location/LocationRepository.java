package com.brokers.api.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    List<Location> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    Optional<Location> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeAndIdNot(UUID organizationId, String code, UUID id);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
