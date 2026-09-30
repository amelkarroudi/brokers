package com.brokers.api.location;

import com.brokers.api.location.LocationDtos.LocationRequest;
import com.brokers.api.location.LocationDtos.LocationView;
import com.brokers.api.security.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<LocationView> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return locationService.list(member.organizationId());
    }

    @GetMapping("/{id}")
    public LocationView get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return locationService.get(member.organizationId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationView create(@AuthenticationPrincipal AuthenticatedMember member,
                               @Valid @RequestBody LocationRequest request) {
        return locationService.create(member.organizationId(), request);
    }

    @PutMapping("/{id}")
    public LocationView update(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id,
                               @Valid @RequestBody LocationRequest request) {
        return locationService.update(member.organizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        locationService.delete(member.organizationId(), id);
    }
}
