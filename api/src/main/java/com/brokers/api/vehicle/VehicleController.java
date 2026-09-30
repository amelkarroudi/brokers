package com.brokers.api.vehicle;

import com.brokers.api.common.PageResponse;
import com.brokers.api.security.AuthenticatedMember;
import com.brokers.api.vehicle.VehicleDtos.PhotoRequest;
import com.brokers.api.vehicle.VehicleDtos.ReorderPhotosRequest;
import com.brokers.api.vehicle.VehicleDtos.VehicleRequest;
import com.brokers.api.vehicle.VehicleDtos.VehicleSummary;
import com.brokers.api.vehicle.VehicleDtos.VehicleView;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public PageResponse<VehicleSummary> list(@AuthenticationPrincipal AuthenticatedMember member,
                                             @RequestParam(required = false) VehicleStatus status,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return vehicleService.list(member.organizationId(), status, search, page, size);
    }

    @GetMapping("/{id}")
    public VehicleView get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return vehicleService.get(member.organizationId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleView create(@AuthenticationPrincipal AuthenticatedMember member,
                              @Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(member.organizationId(), request);
    }

    @PutMapping("/{id}")
    public VehicleView update(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id,
                              @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(member.organizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        vehicleService.archive(member.organizationId(), id);
    }

    @PostMapping("/{id}/publish")
    public VehicleView publish(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return vehicleService.publish(member.organizationId(), id);
    }

    @PostMapping("/{id}/unpublish")
    public VehicleView unpublish(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return vehicleService.unpublish(member.organizationId(), id);
    }

    @PostMapping("/{id}/sync")
    public VehicleView resync(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return vehicleService.resync(member.organizationId(), id);
    }

    @PostMapping("/{id}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleView addPhoto(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id,
                                @Valid @RequestBody PhotoRequest request) {
        return vehicleService.addPhoto(member.organizationId(), id, request);
    }

    @PutMapping("/{id}/photos/order")
    public VehicleView reorderPhotos(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id,
                                     @Valid @RequestBody ReorderPhotosRequest request) {
        return vehicleService.reorderPhotos(member.organizationId(), id, request);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public VehicleView removePhoto(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id,
                                   @PathVariable UUID photoId) {
        return vehicleService.removePhoto(member.organizationId(), id, photoId);
    }
}
