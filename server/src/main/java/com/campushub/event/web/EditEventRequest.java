package com.campushub.event.web;

import com.campushub.event.domain.EventEdit;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

// A sparse PATCH body: any absent (null) field is left unchanged. See EventEdit, which this maps to
// directly — the guard for what's actually allowed to change lives in EventRepository's query filter.
record EditEventRequest(
        String title,
        String description,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant startsAt,
        Instant endsAt,
        @Positive Integer capacity) {

    EventEdit toEventEdit() {
        return new EventEdit(
                title, description, registrationOpensAt, registrationClosesAt, startsAt, endsAt, capacity);
    }
}
