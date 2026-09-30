package com.brokers.api.webhook;

import com.brokers.channel.core.Channel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByConnectionIdAndExternalEventId(UUID connectionId, String externalEventId);

    Optional<WebhookEvent> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query("""
            select e from WebhookEvent e
            where e.organizationId = :organizationId
              and (:status is null or e.status = :status)
              and (:channel is null or e.channel = :channel)
            """)
    Page<WebhookEvent> search(UUID organizationId, WebhookEventStatus status, Channel channel, Pageable pageable);

    long countByOrganizationIdAndStatus(UUID organizationId, WebhookEventStatus status);
}
