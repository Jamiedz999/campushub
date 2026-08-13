package com.campushub.identityaccess.web;

import com.campushub.identityaccess.IdentityAccessModule;
import com.campushub.identityaccess.domain.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Login and logout are handled by the Security filter chain itself (see SecurityConfig) — this
// controller only exposes read access to who is currently signed in.
@RestController
class AuthController {

    private final IdentityAccessModule identityAccessModule;

    AuthController(IdentityAccessModule identityAccessModule) {
        this.identityAccessModule = identityAccessModule;
    }

    @GetMapping("/api/auth/me")
    CurrentActorResponse currentActor() {
        CurrentActor actor = identityAccessModule.currentActor();
        return new CurrentActorResponse(
                actor.accountId(), actor.email(), actor.displayName(), actor.systemRole(), actor.officerClubIds());
    }
}
