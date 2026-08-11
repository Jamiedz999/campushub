# CH-021 · Add sign-in, roles and Club grants

Sprint: 1
Area: identityaccess, club
Blocked by: 020
Decisions: [roles and authorization](../../adr/08-define-roles-and-resource-authorization.md), [technical baseline](../implementation/TECHNICAL-BASELINE.md)

## Change

- `identityaccess` module: accounts, password hashing, Spring Security form login, Spring Session MongoDB, CSRF enabled, secure cookies. The current actor carries their Club grants.
- `club` module: Club documents, and Club Officer grants held **per Club**. A University Admin grants and revokes them.
- Seeded data: two Clubs, one University Admin, two Club Officers, several Students. Seeding is a Mongock change unit so it is versioned, not a script.
- Frontend: sign-in page, session-aware routing, a shared axios instance carrying the CSRF header and normalising errors.

## Acceptance

- Revoking an officer grant takes effect on the **next request**, with no token expiry to wait for.
- A Student holding grants in two Clubs is representable and correct.
- Unauthenticated access to any internal route is refused.

## Tests

Integration tests for login, logout, CSRF rejection and immediate revocation. Negative test: a Student cannot reach an officer-only route.
