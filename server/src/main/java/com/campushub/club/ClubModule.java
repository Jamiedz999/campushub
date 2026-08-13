package com.campushub.club;

import java.util.List;
import java.util.Set;

// Grants and clubs never cross the module boundary as documents — only their identifiers do, so no
// peer module can come to depend on Club's storage shape. See
// docs/adr/08-define-roles-and-resource-authorization.md and
// docs/planning/implementation/TECHNICAL-BASELINE.md (club module: "Club documents and officer grants").
//
// club deliberately knows nothing about accounts, roles or the current actor — that dependency would
// point back at identityaccess (which already depends on club, for officerClubIdsFor), and Spring
// Modulith rejects the cycle. The consequence: every method below is scoped by clubId alone, not by
// caller. It is NOT safe to expose one of these directly to a caller without checking entitlement
// first — see identityaccess.web.ClubOfficerController for the pattern (resolve CurrentActor, check
// isEntitledToClub, only then call into this module).
public interface ClubModule {

    /** Creates a Club and returns its id. */
    String createClub(String name);

    /** Grants Club Officer rights in {@code clubId} to {@code accountId}. Idempotent. */
    void grantOfficer(String clubId, String accountId);

    /** Revokes Club Officer rights in {@code clubId} from {@code accountId}, if held. */
    void revokeOfficer(String clubId, String accountId);

    /** The account ids currently holding Club Officer rights in {@code clubId}. Not caller-scoped. */
    List<String> officersOf(String clubId);

    /** The ids of every Club {@code accountId} currently officers, possibly more than one. */
    Set<String> officerClubIdsFor(String accountId);
}
