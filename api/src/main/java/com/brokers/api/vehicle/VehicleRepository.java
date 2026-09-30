package com.brokers.api.vehicle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndReference(UUID organizationId, String reference);

    boolean existsByOrganizationIdAndReferenceAndIdNot(UUID organizationId, String reference, UUID id);

    boolean existsByLocationId(UUID locationId);

    @Query("""
            select v from Vehicle v
            where v.organizationId = :organizationId
              and (:status is null or v.status = :status)
              and (:search is null
                   or lower(v.reference) like :search
                   or lower(v.make) like :search
                   or lower(v.model) like :search)
            """)
    Page<Vehicle> search(UUID organizationId, VehicleStatus status, String search, Pageable pageable);

    @Query("select v.id from Vehicle v where v.organizationId = :organizationId and v.status = :status")
    List<UUID> findIdsByStatus(UUID organizationId, VehicleStatus status);

    @Query("""
            select v.id from Vehicle v
            where v.organizationId = :organizationId and v.locationId = :locationId
              and v.status = com.brokers.api.vehicle.VehicleStatus.ACTIVE
            """)
    List<UUID> findActiveIdsByLocation(UUID organizationId, UUID locationId);

    long countByOrganizationIdAndStatus(UUID organizationId, VehicleStatus status);
}
