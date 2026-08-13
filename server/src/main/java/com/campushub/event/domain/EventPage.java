package com.campushub.event.domain;

import java.util.List;

// The collection envelope docs/adr/15-define-http-api-and-time-contract.md fixes for every paged
// endpoint: items, zero-indexed page, size, and a total that must agree with items even when the
// non-indexable free-seat $expr filter is active.
public record EventPage(List<Event> items, int page, int size, long total) {

    public EventPage {
        items = List.copyOf(items);
    }
}
