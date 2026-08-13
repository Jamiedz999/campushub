package com.campushub.event.domain;

import java.time.Instant;

// Every predicate here goes into the single $match stage docs/adr/16-define-event-discovery.md
// requires. A null field means "no filter". `sort` may be null, meaning "let the module decide" —
// relevance when searchTerm is present, startsAt ascending otherwise.
public record EventBrowseQuery(
        String searchTerm,
        String clubId,
        Boolean openForRegistration,
        Instant startsAtFrom,
        Instant startsAtTo,
        Boolean hasFreeSeat,
        EventSort sort,
        int page,
        int size) {}
