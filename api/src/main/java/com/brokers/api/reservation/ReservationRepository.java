package com.brokers.api.reservation;

import com.brokers.channel.core.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByChannelAndExternalReservationId(Channel channel, String externalReservationId);

    Optional<Reservation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select count(r) > 0 from Reservation r
            where r.vehicleId = :vehicleId and r.status = com.brokers.api.reservation.ReservationStatus.CONFIRMED
              and r.returnAt > :now
            """)
    boolean existsUpcomingConfirmed(UUID vehicleId, Instant now);

    @Query("""
            select r from Reservation r
            where r.vehicleId = :vehicleId and r.status = com.brokers.api.reservation.ReservationStatus.CONFIRMED
              and r.returnAt > :after
            """)
    List<Reservation> findConfirmedEndingAfter(UUID vehicleId, Instant after);

    @Query("""
            select r from Reservation r
            where r.organizationId = :organizationId
              and (:status is null or r.status = :status)
              and (:vehicleId is null or r.vehicleId = :vehicleId)
              and (:conflictOnly = false or r.hasConflict = true)
            """)
    Page<Reservation> search(UUID organizationId, ReservationStatus status, UUID vehicleId, boolean conflictOnly,
                             Pageable pageable);

    @Query("""
            select count(r) from Reservation r
            where r.organizationId = :organizationId and r.status = com.brokers.api.reservation.ReservationStatus.CONFIRMED
              and r.pickupAt >= :now
            """)
    long countUpcoming(UUID organizationId, Instant now);

    long countByOrganizationIdAndHasConflictTrue(UUID organizationId);
}
