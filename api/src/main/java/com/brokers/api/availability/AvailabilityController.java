package com.brokers.api.availability;

import com.brokers.api.availability.AvailabilityDtos.BlockRequest;
import com.brokers.api.availability.AvailabilityDtos.BlockView;
import com.brokers.api.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles/{vehicleId}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<BlockView> list(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID vehicleId,
                                @RequestParam(required = false) Instant from,
                                @RequestParam(required = false) Instant to) {
        return availabilityService.list(member.organizationId(), vehicleId, from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockView create(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID vehicleId,
                            @Valid @RequestBody BlockRequest request) {
        return availabilityService.create(member.organizationId(), vehicleId, request);
    }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID vehicleId,
                       @PathVariable UUID blockId) {
        availabilityService.delete(member.organizationId(), vehicleId, blockId);
    }
}
