package com.brokers.api.sync;

import com.brokers.api.config.BrokersProperties;
import com.brokers.api.connection.ChannelConnectionRepository;
import com.brokers.api.listing.ChannelListingRepository;
import com.brokers.channel.core.error.ChannelErrorKind;
import com.brokers.channel.core.error.ChannelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Drains the sync job queue. Each job is claimed atomically, executed, and then marked
 * succeeded, rescheduled with backoff, or failed depending on how the channel responded.
 */
@Component
public class SyncWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);

    private final SyncJobRepository jobs;
    private final ChannelConnectionRepository connections;
    private final ChannelListingRepository listings;
    private final SyncExecutor executor;
    private final SyncBackoff backoff;
    private final TransactionTemplate transactions;
    private final BrokersProperties.Sync settings;
    private final Clock clock;

    SyncWorker(SyncJobRepository jobs, ChannelConnectionRepository connections, ChannelListingRepository listings,
               SyncExecutor executor, SyncBackoff backoff, TransactionTemplate transactions,
               BrokersProperties properties, Clock clock) {
        this.jobs = jobs;
        this.connections = connections;
        this.listings = listings;
        this.executor = executor;
        this.backoff = backoff;
        this.transactions = transactions;
        this.settings = properties.sync();
        this.clock = clock;
    }

    /** Runs one batch of due jobs and returns how many were processed. */
    public int runDueJobs() {
        recoverStaleJobs();
        List<UUID> due = jobs.findDueIds(clock.instant(), PageRequest.of(0, settings.batchSize()));
        int processed = 0;
        for (UUID jobId : due) {
            Integer claimed = transactions.execute(status -> jobs.claim(jobId, clock.instant()));
            if (claimed == null || claimed == 0) {
                continue;
            }
            process(jobId);
            processed++;
        }
        return processed;
    }

    /** Runs batches until no job is due. Used by tests and the sandbox to settle the queue. */
    public int runUntilIdle() {
        int total = 0;
        for (int round = 0; round < 100; round++) {
            int processed = runDueJobs();
            if (processed == 0) {
                return total;
            }
            total += processed;
        }
        return total;
    }

    private void process(UUID jobId) {
        try {
            executor.execute(jobId);
            complete(jobId, job -> job.succeed(clock.instant()));
        } catch (ChannelException e) {
            handleChannelFailure(jobId, e);
        } catch (SyncAbortedException e) {
            log.info("Sync job {} aborted: {}", jobId, e.getMessage());
            complete(jobId, job -> job.fail(e.getMessage(), clock.instant()));
        } catch (RuntimeException e) {
            log.error("Sync job {} failed unexpectedly", jobId, e);
            retryOrFail(jobId, e.getMessage(), null);
        }
    }

    private void handleChannelFailure(UUID jobId, ChannelException e) {
        log.warn("Sync job {} failed ({}): {}", jobId, e.kind(), e.getMessage());
        if (e.kind() == ChannelErrorKind.AUTHENTICATION) {
            transactions.executeWithoutResult(status -> {
                SyncJob job = jobs.findById(jobId).orElseThrow();
                connections.findByOrganizationIdAndChannel(job.getOrganizationId(), job.getChannel())
                        .ifPresent(connection -> connection.markInvalidCredentials(e.getMessage()));
                job.fail(e.getMessage(), clock.instant());
            });
            return;
        }
        if (e.retryable()) {
            retryOrFail(jobId, e.getMessage(), e.retryAfter().orElse(null));
            return;
        }
        failWithListing(jobId, e.getMessage());
    }

    private void retryOrFail(UUID jobId, String error, Duration retryAfter) {
        transactions.executeWithoutResult(status -> {
            SyncJob job = jobs.findById(jobId).orElseThrow();
            if (!job.hasAttemptsLeft()) {
                job.fail("Gave up after " + job.getAttempts() + " attempts: " + error, clock.instant());
                markListingFailed(job, error);
                return;
            }
            Instant next = clock.instant().plus(backoff.delay(job.getAttempts(), retryAfter));
            job.retryAt(next, error);
        });
    }

    private void failWithListing(UUID jobId, String error) {
        transactions.executeWithoutResult(status -> {
            SyncJob job = jobs.findById(jobId).orElseThrow();
            job.fail(error, clock.instant());
            markListingFailed(job, error);
        });
    }

    private void markListingFailed(SyncJob job, String error) {
        if (job.getType() == SyncJobType.DEACTIVATE_LISTING) {
            return;
        }
        listings.findByVehicleIdAndChannel(job.getVehicleId(), job.getChannel())
                .ifPresent(listing -> listing.markFailed(error));
    }

    private void complete(UUID jobId, Consumer<SyncJob> change) {
        transactions.executeWithoutResult(status -> change.accept(jobs.findById(jobId).orElseThrow()));
    }

    private void recoverStaleJobs() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(settings.staleLockTimeout());
        transactions.executeWithoutResult(status -> {
            int failed = jobs.failStale(staleBefore, now);
            int released = jobs.releaseStale(staleBefore, now);
            if (failed + released > 0) {
                log.warn("Recovered stale sync jobs: {} requeued, {} failed", released, failed);
            }
        });
    }
}
