package com.brokers.api.connection;

import com.brokers.channel.core.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelConnectionRepository extends JpaRepository<ChannelConnection, UUID> {

    List<ChannelConnection> findByOrganizationId(UUID organizationId);

    Optional<ChannelConnection> findByOrganizationIdAndChannel(UUID organizationId, Channel channel);

    Optional<ChannelConnection> findByIdAndChannel(UUID id, Channel channel);

    Optional<ChannelConnection> findByChannelAndAccountId(Channel channel, String accountId);

    List<ChannelConnection> findByOrganizationIdAndStatus(UUID organizationId, ConnectionStatus status);
}
