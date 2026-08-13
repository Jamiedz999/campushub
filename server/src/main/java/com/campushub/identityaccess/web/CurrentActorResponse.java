package com.campushub.identityaccess.web;

import com.campushub.identityaccess.domain.SystemRole;
import java.util.Set;

record CurrentActorResponse(
        String accountId, String email, String displayName, SystemRole systemRole, Set<String> officerClubIds) {}
