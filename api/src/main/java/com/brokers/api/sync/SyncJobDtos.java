package com.brokers.api.sync;

import com.brokers.channel.core.Channel;

import java.time.Instant;
import java.util.UUID;

public final class SyncJobDtos {

    private SyncJobDtos() {
    }

    public record SyncJobView(
            UUID id,
            UUID vehicleId,
            Channel channel,
            SyncJobType type,
            SyncJobStatus status,
            SyncTrigger trigger,
            boolean force,
            int attempts,
            int maxAttempts,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant completedAt) {

        static SyncJobView of(SyncJob job) {
            return new SyncJobView(job.getId(), job.getVehicleId(), job.getChannel(), job.getType(), job.getStatus(),
                    job.getTrigger(), job.isForce(), job.getAttempts(), job.getMaxAttempts(), job.getNextAttemptAt(),
                    job.getLastError(), job.getCreatedAt(), job.getCompletedAt());
        }
    }
}
