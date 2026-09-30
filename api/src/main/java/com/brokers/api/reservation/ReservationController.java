package com.brokers.api.reservation;

import com.brokers.api.common.PageResponse;
import com.brokers.api.reservation.ReservationDtos.ReservationView;
import com.brokers.api.security.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reservations are read-only here: they are created and changed by the channels via webhooks. */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public PageResponse<ReservationView> list(@AuthenticationPrincipal AuthenticatedMember member,
                                              @RequestParam(required = false) ReservationStatus status,
                                              @RequestParam(required = false) UUID vehicleId,
                                              @RequestParam(defaultValue = "false") boolean conflictOnly,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return reservationService.list(member.organizationId(), status, vehicleId, conflictOnly, page, size);
    }

    @GetMapping("/{id}")
    public ReservationView get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return reservationService.get(member.organizationId(), id);
    }
}
