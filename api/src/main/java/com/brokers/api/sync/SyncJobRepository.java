package com.brokers.api.sync;

import com.brokers.channel.core.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    Optional<SyncJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<SyncJob> findFirstByVehicleIdAndChannelAndTypeAndStatus(
            UUID vehicleId, Channel channel, SyncJobType type, SyncJobStatus status);

    /** Due jobs, skipping vehicles that already have a job running on the same channel. */
    @Query("""
            select j.id from SyncJob j
            where j.status = com.brokers.api.sync.SyncJobStatus.PENDING and j.nextAttemptAt <= :now
              and not exists (
                  select 1 from SyncJob running
                  where running.vehicleId = j.vehicleId and running.channel = j.channel
                    and running.status = com.brokers.api.sync.SyncJobStatus.RUNNING)
            order by j.nextAttemptAt asc
            """)
    List<UUID> findDueIds(Instant now, Pageable pageable);

    /**
     * Atomically moves a job from PENDING to RUNNING. Returns 0 when another worker claimed it
     * first, which makes it safe to run several worker instances.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update SyncJob j
            set j.status = com.brokers.api.sync.SyncJobStatus.RUNNING, j.lockedAt = :now,
                j.attempts = j.attempts + 1, j.version = j.version + 1
            where j.id = :id and j.status = com.brokers.api.sync.SyncJobStatus.PENDING
            """)
    int claim(UUID id, Instant now);

    /** Returns jobs abandoned by a crashed worker to the queue. */
    @Modifying(clearAutomatically = true)
    @Query("""
            update SyncJob j
            set j.status = com.brokers.api.sync.SyncJobStatus.PENDING, j.lockedAt = null,
                j.nextAttemptAt = :now, j.version = j.version + 1
            where j.status = com.brokers.api.sync.SyncJobStatus.RUNNING and j.lockedAt < :staleBefore
              and j.attempts < j.maxAttempts
            """)
    int releaseStale(Instant staleBefore, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update SyncJob j
            set j.status = com.brokers.api.sync.SyncJobStatus.FAILED, j.lockedAt = null, j.completedAt = :now,
                j.lastError = 'Worker stopped while running the job and no attempts are left',
                j.version = j.version + 1
            where j.status = com.brokers.api.sync.SyncJobStatus.RUNNING and j.lockedAt < :staleBefore
              and j.attempts >= j.maxAttempts
            """)
    int failStale(Instant staleBefore, Instant now);

    @Query("""
            select j from SyncJob j
            where j.organizationId = :organizationId
              and (:status is null or j.status = :status)
              and (:vehicleId is null or j.vehicleId = :vehicleId)
              and (:channel is null or j.channel = :channel)
            """)
    Page<SyncJob> search(UUID organizationId, SyncJobStatus status, UUID vehicleId, Channel channel, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, SyncJobStatus status);
}
