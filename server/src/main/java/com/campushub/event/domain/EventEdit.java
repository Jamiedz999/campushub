package com.campushub.event.domain;

import java.time.Instant;

// A sparse edit command: any null field means "leave unchanged". See
// docs/adr/03-define-event-lifecycle.md — which fields may change depends on Status and on `now`
// against startsAt, so the guard lives in the persistence-layer query filter, not here. This record is
// only the "what to change", never the "whether it's allowed".
public record EventEdit(
        String title,
        String description,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        Instant startsAt,
        Instant endsAt,
        Integer capacity) {}
