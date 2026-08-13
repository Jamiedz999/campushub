package com.campushub.identityaccess.domain;

import java.util.Set;

// Resolved fresh on every request from the session's account id plus a live query of Club grants —
// never cached in the session itself, which is what lets a revoked grant take effect on the very next
// request with no token expiry to wait for. See docs/adr/08-define-roles-and-resource-authorization.md.
public record CurrentActor(
        String accountId, String email, String displayName, SystemRole systemRole, Set<String> officerClubIds) {

    // Defensively copied to an immutable Set: a caller mutating what officerClubIds() returns must
    // never be able to make this "fresh per request" value object lie about a grant.
    public CurrentActor {
        officerClubIds = Set.copyOf(officerClubIds);
    }

    public boolean isUniversityAdmin() {
        return systemRole == SystemRole.UNIVERSITY_ADMIN;
    }

    public boolean isOfficerOf(String clubId) {
        return officerClubIds.contains(clubId);
    }

    /** The rule behind every Club-scoped endpoint: a University Admin, or that Club's own Officer. */
    public boolean isEntitledToClub(String clubId) {
        return isUniversityAdmin() || isOfficerOf(clubId);
    }
}
