package com.campushub.event.web;

import com.campushub.event.EventModule;
import com.campushub.event.domain.EventBrowseQuery;
import com.campushub.event.domain.EventPage;
import com.campushub.event.domain.EventSort;
import com.campushub.shared.PageResponse;
import java.time.Clock;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Published Events only, per docs/adr/16-define-event-discovery.md — status: Published is not a
// caller-facing filter, it is what this endpoint always matches. Requires authentication like every
// other /api/** route (see identityaccess.internal.SecurityConfig); Core has no public marketing page.
@RestController
class EventBrowseController {

    private final EventModule eventModule;
    private final Clock clock;

    EventBrowseController(EventModule eventModule, Clock clock) {
        this.eventModule = eventModule;
        this.clock = clock;
    }

    @GetMapping("/api/events")
    PageResponse<EventBrowseItemResponse> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String clubId,
            @RequestParam(required = false) Boolean openForRegistration,
            @RequestParam(required = false) Instant startsAtFrom,
            @RequestParam(required = false) Instant startsAtTo,
            @RequestParam(required = false) Boolean hasFreeSeat,
            @RequestParam(required = false) EventSort sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        EventBrowseQuery query = new EventBrowseQuery(
                q, clubId, openForRegistration, startsAtFrom, startsAtTo, hasFreeSeat, sort, page, size);
        EventPage result = eventModule.browse(query);
        Instant now = clock.instant();
        return new PageResponse<>(
                result.items().stream()
                        .map(event -> EventBrowseItemResponse.from(event, now))
                        .toList(),
                result.page(),
                result.size(),
                result.total());
    }
}
