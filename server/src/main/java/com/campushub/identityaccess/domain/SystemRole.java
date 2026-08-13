package com.campushub.identityaccess.domain;

// Everyone signed in is a Student; University Admin is the one campus-wide elevation. Club Officer is
// deliberately not a value here — it is a per-Club grant, not a system role. See
// docs/adr/08-define-roles-and-resource-authorization.md.
public enum SystemRole {
    STUDENT,
    UNIVERSITY_ADMIN
}
