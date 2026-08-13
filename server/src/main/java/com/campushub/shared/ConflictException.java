package com.campushub.shared;

// A 409 refusal whose specific reason is a stable `code` — see
// docs/adr/15-define-http-api-and-time-contract.md: several distinct outcomes (Event full, already
// enrolled, Registration Window closed, Event cancelled, ...) share the 409 status, and the frontend
// switches on `code` to tell them apart, never on `detail` or on the status alone.
public class ConflictException extends RuntimeException {

    private final ErrorCode code;

    public ConflictException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
