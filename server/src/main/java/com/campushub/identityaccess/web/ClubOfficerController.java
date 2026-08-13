package com.campushub.identityaccess.web;

import com.campushub.club.ClubModule;
import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import com.campushub.shared.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Lives in identityaccess, not club, on purpose: answering "who may see or change this" needs the
// caller's system role, which only identityaccess resolves. Putting it in club instead would make club
// depend on identityaccess, and identityaccess already depends on club (for officerClubIdsFor when
// building CurrentActor) — that pairing would be a module cycle, which
// docs/planning/implementation/TECHNICAL-BASELINE.md's Spring Modulith verification rejects outright.
// club stays a dependency-free, dumb store of Club and grant documents; every entitlement check that
// gates a ClubModule call happens here, before the call, never inside club.
//
// Authorization failures are 404 NOT_FOUND here, never 403 — see
// docs/adr/08-define-roles-and-resource-authorization.md and the API contract table in
// docs/planning/implementation/TECHNICAL-BASELINE.md.
@RestController
class ClubOfficerController {

    private final IdentityAccessModule identityAccessModule;
    private final ClubModule clubModule;

    ClubOfficerController(IdentityAccessModule identityAccessModule, ClubModule clubModule) {
        this.identityAccessModule = identityAccessModule;
        this.clubModule = clubModule;
    }

    @GetMapping("/api/clubs/{clubId}/officers")
    ClubOfficersResponse officersOf(@PathVariable String clubId) {
        CurrentActor actor = identityAccessModule.currentActor();
        if (!actor.isEntitledToClub(clubId)) {
            throw new NotFoundException("No such club, or the caller may not view its officers.");
        }
        return new ClubOfficersResponse(clubModule.officersOf(clubId));
    }

    @PostMapping("/api/clubs/{clubId}/officers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void grantOfficer(@PathVariable String clubId, @Valid @RequestBody GrantOfficerRequest request) {
        requireUniversityAdmin();
        clubModule.grantOfficer(clubId, request.accountId());
    }

    @DeleteMapping("/api/clubs/{clubId}/officers/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeOfficer(@PathVariable String clubId, @PathVariable String accountId) {
        requireUniversityAdmin();
        clubModule.revokeOfficer(clubId, accountId);
    }

    private void requireUniversityAdmin() {
        if (!identityAccessModule.currentActor().isUniversityAdmin()) {
            throw new NotFoundException("Only a University Admin may grant or revoke Club Officer rights.");
        }
    }
}
