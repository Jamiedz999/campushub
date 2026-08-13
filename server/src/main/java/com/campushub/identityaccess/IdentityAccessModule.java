package com.campushub.identityaccess;

import com.campushub.identityaccess.domain.CurrentActor;
import java.util.Map;
import java.util.Set;

public interface IdentityAccessModule {

    /**
     * The signed-in caller, resolved fresh from the database on every call — never cached in the
     * session. Only valid to call within an authenticated request. See
     * docs/adr/08-define-roles-and-resource-authorization.md.
     */
    CurrentActor currentActor();

    /** Display names for Officer-only Registration answer reports; absent ids remain absent. */
    Map<String, String> displayNames(Set<String> accountIds);
}
