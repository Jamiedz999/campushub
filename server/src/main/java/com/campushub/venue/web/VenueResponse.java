package com.campushub.venue.web;

import com.campushub.venue.VenueModule.VenueSummary;

record VenueResponse(String id, String name) {

    static VenueResponse from(VenueSummary venue) {
        return new VenueResponse(venue.id(), venue.name());
    }
}
