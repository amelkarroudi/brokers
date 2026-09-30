package com.brokers.api.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    Optional<AvailabilityBlock> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<AvailabilityBlock> findByReservationId(UUID reservationId);

    /** Blocks intersecting {@code [from, to)}. */
    @Query("""
            select b from AvailabilityBlock b
            where b.vehicleId = :vehicleId and b.startsAt < :to and b.endsAt > :from
            order by b.startsAt asc
            """)
    List<AvailabilityBlock> findOverlapping(UUID vehicleId, Instant from, Instant to);

    @Query("""
            select count(b) > 0 from AvailabilityBlock b
            where b.vehicleId = :vehicleId and b.startsAt < :to and b.endsAt > :from
              and (:excludeId is null or b.id <> :excludeId)
            """)
    boolean existsOverlapping(UUID vehicleId, Instant from, Instant to, UUID excludeId);
}
