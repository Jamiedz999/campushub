package com.campushub.event.domain;

// Stored, forward-only: Draft -> Published -> Cancelled, with Cancelled also reachable directly from
// Draft's sibling transition. See docs/adr/03-define-event-lifecycle.md — there is deliberately no
// un-publishing transition, so no method here offers one.
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED
}
