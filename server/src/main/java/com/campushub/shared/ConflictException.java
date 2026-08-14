package com.campushub.shared;

import java.util.Map;

// A 409 refusal whose specific reason is a stable `code` — see
// docs/adr/15-define-http-api-and-time-contract.md: several distinct outcomes (Event full, already
// enrolled, Registration Window closed, Event cancelled, ...) share the 409 status, and the frontend
// switches on `code` to tell them apart, never on `detail` or on the status alone.
public class ConflictException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> extensions;

    public ConflictException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /**
     * With extra problem+json members, for the refusals that carry a fact the client needs and cannot
     * read out of `detail` — a second scan is told when the first one was, rather than only that it
     * happened.
     */
    public ConflictException(ErrorCode code, String message, Map<String, Object> extensions) {
        super(message);
        this.code = code;
        this.extensions = Map.copyOf(extensions);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> extensions() {
        return extensions;
    }
}
