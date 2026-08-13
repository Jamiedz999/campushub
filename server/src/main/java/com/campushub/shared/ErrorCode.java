package com.campushub.shared;

// The stable "code" extension member every application/problem+json response carries.
public enum ErrorCode {

    VALIDATION_FAILED,
    INTERNAL_ERROR,
    // No session at all — the caller never authenticated.
    UNAUTHENTICATED,
    // The submitted email/password did not match an account.
    INVALID_CREDENTIALS,
    // Authenticated, but not entitled — see docs/adr/08-define-roles-and-resource-authorization.md:
    // ownership is enforced by scoping the query, so an unentitled caller sees "not found", never 403.
    NOT_FOUND,
    // The caller is entitled to the resource, but its current lifecycle state refuses this write — see
    // docs/adr/03-define-event-lifecycle.md: publishing an already-Published Event, editing a locked
    // field past startsAt, or lowering Capacity once Published.
    EVENT_NOT_EDITABLE,
    // Taking a Seat was refused — see docs/adr/04-define-registration-capacity-and-waitlist.md and the
    // Seat Ledger row of ADR 15's code table. All six are 409: several distinct reasons share one status.
    EVENT_FULL,
    ALREADY_ENROLLED,
    ALREADY_WAITLISTED,
    REGISTRATION_NOT_OPEN,
    REGISTRATION_CLOSED,
    EVENT_STARTED,
    EVENT_CANCELLED
}
