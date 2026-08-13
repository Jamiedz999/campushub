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
    NOT_FOUND
}
