package com.campushub.event.domain;

// What a guarded Event write reported. NOT_FOUND covers both "no such id" and "not one of the caller's
// Clubs" indistinguishably, per docs/adr/08-define-roles-and-resource-authorization.md — the caller
// never learns which. NOT_EDITABLE covers every other reason the lifecycle guard refused the write:
// wrong Status for the command, or past the startsAt/endsAt freeze.
public enum EventCommandResult {
    SUCCESS,
    NOT_FOUND,
    NOT_EDITABLE
}
