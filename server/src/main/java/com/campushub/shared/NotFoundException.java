package com.campushub.shared;

// Thrown wherever a caller is not entitled to a resource — see
// docs/adr/08-define-roles-and-resource-authorization.md: authorization failure is 404, never 403,
// because the caller's grants are supposed to scope the query rather than being checked afterward.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
