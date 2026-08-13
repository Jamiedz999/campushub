package com.campushub.identityaccess;

import com.campushub.identityaccess.domain.CurrentActor;

public interface IdentityAccessModule {

    /**
     * The signed-in caller, resolved fresh from the database on every call — never cached in the
     * session. Only valid to call within an authenticated request. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    CurrentActor currentActor();
}
