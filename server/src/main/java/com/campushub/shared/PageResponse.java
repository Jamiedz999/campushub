package com.campushub.shared;

import java.util.List;

// The one collection envelope every paged endpoint in the API uses — see
// docs/adr/15-define-http-api-and-time-contract.md: {items, page, size, total}, zero-indexed page.
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public PageResponse {
        items = List.copyOf(items);
    }
}
