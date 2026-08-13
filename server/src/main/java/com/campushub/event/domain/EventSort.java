package com.campushub.event.domain;

// The three sort keys docs/adr/16-define-event-discovery.md offers, plus RELEVANCE (text score),
// available only when a search term is present. Sorting by seats remaining is deliberately not offered.
public enum EventSort {
    STARTS_AT_ASC,
    STARTS_AT_DESC,
    CREATED_AT_DESC,
    RELEVANCE
}
