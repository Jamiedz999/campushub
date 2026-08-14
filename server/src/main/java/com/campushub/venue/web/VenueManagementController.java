package com.campushub.venue.web;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.NotFoundException;
import com.campushub.shared.PageResponse;
import com.campushub.venue.VenueModule;
import com.campushub.venue.VenueModule.VenueSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VenueManagementController {

    private final IdentityAccessModule identityAccessModule;
    private final VenueModule venueModule;

    VenueManagementController(IdentityAccessModule identityAccessModule, VenueModule venueModule) {
        this.identityAccessModule = identityAccessModule;
        this.venueModule = venueModule;
    }

    @PostMapping("/api/venues")
    @ResponseStatus(HttpStatus.CREATED)
    VenueResponse create(@Valid @RequestBody SaveVenueRequest request) {
        requireUniversityAdmin();
        String venueId = venueModule.createVenue(request.name());
        VenueSummary created = venueModule
                .findVenue(venueId)
                .orElseThrow(() -> new NotFoundException("The newly created Venue could not be read."));
        return VenueResponse.from(created);
    }

    @GetMapping("/api/venues")
    PageResponse<VenueResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentActor actor = identityAccessModule.currentActor();
        if (!actor.isUniversityAdmin() && actor.officerClubIds().isEmpty()) {
            throw new NotFoundException("The Venue booking surface is available only to Officers and Admins.");
        }
        VenueModule.VenuePage venues = venueModule.listVenues(page, size);
        return new PageResponse<>(
                venues.items().stream().map(VenueResponse::from).toList(),
                venues.page(),
                venues.size(),
                venues.total());
    }

    @PatchMapping("/api/venues/{venueId}")
    VenueResponse rename(
            @PathVariable String venueId, @Valid @RequestBody SaveVenueRequest request) {
        requireUniversityAdmin();
        if (!venueModule.renameVenue(venueId, request.name())) {
            throw new NotFoundException("No such Venue.");
        }
        return venueModule
                .findVenue(venueId)
                .map(VenueResponse::from)
                .orElseThrow(() -> new NotFoundException("No such Venue."));
    }

    private void requireUniversityAdmin() {
        if (!identityAccessModule.currentActor().isUniversityAdmin()) {
            throw new NotFoundException("No such Venue management operation.");
        }
    }
}
