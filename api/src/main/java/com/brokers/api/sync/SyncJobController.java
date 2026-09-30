package com.brokers.api.sync;

import com.brokers.api.common.ApiException;
import com.brokers.api.common.PageResponse;
import com.brokers.api.common.Pagination;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.api.sync.SyncJobDtos.SyncJobView;
import com.brokers.channel.core.Channel;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/** The sync log: every push to a channel, its outcome, and manual retry of failures. */
@RestController
@RequestMapping("/api/sync-jobs")
public class SyncJobController {

    private final SyncJobRepository jobs;
    private final Clock clock;

    public SyncJobController(SyncJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<SyncJobView> list(@AuthenticationPrincipal AuthenticatedMember member,
                                          @RequestParam(required = false) SyncJobStatus status,
                                          @RequestParam(required = false) UUID vehicleId,
                                          @RequestParam(required = false) Channel channel,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(jobs.search(member.organizationId(), status, vehicleId, channel,
                        Pagination.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                SyncJobView::of);
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public SyncJobView retry(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        SyncJob job = jobs.findByIdAndOrganizationId(id, member.organizationId())
                .orElseThrow(() -> ApiException.notFound("Sync job"));
        if (job.getStatus() != SyncJobStatus.FAILED) {
            throw ApiException.conflict("not_retryable", "Only failed jobs can be retried");
        }
        job.requeue(clock.instant());
        return SyncJobView.of(job);
    }
}
