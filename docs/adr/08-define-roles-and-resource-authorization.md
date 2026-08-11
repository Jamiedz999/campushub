# Define the three roles and resource-level authorization

Type: grilling
Status: resolved
Blocked by: none

## Question

What are the three roles, what may each do, and how is "a club officer may only touch their own club's resources" enforced?

## Answer

### Club Officer is a grant, not a global role

Everyone signed in is a **Student**. A Student additionally holds **Club Officer** rights in zero or more Clubs, each grant naming one Club. A **University Admin** is campus-wide.

Modelling the officer role as a per-Club grant rather than a global flag is what makes resource-level authorization real rather than decorative: authority is always *over something*, and one person may legitimately run two societies.

**University Admins grant officer rights.** Self-service Club registration is out of scope, so Clubs and their first officers are seeded; after that, granting is the third substantive University Admin responsibility alongside managing Venues and reading the cross-club dashboard. Approving Events is not one of them.

### The permission matrix

| Operation | Student | Club Officer | University Admin |
|---|---|---|---|
| Browse and view Published Events | ✅ | ✅ | ✅ |
| Register, withdraw, join Waitlist | ✅ | ✅ | ✅ |
| Check in to an Event they are enrolled in | ✅ | ✅ | ✅ |
| See attendee names or form answers | ❌ | own Club only | ❌ |
| Create, edit, publish, cancel an Event | ❌ | own Club only | cancel only |
| Build the registration form, export answers | ❌ | own Club only | ❌ |
| Book or release a Venue Slot | ❌ | own Club only | ✅ |
| Run the door screen, override attendance | ❌ | own Club only | ❌ |
| Create and manage Venues | ❌ | ❌ | ✅ |
| Grant Club Officer rights | ❌ | ❌ | ✅ |
| Read the cross-club dashboard | ❌ | own Club only | ✅ |

A University Admin can cancel any Event, because they own the Venues and must be able to clear a room. They cannot author or run one; that is the Club's business.

### Enforcement: scope the query, do not check after reading

Every Club Officer operation is expressed as a query already filtered by the set of Club identifiers the caller holds. An Event belonging to another Club is **not found**, rather than found and then refused.

The rejected pattern is load-then-check: fetch by id, compare the owner, throw if it differs. It works exactly as long as every current and future caller remembers to do it, and it fails silently and invisibly the first time one does not. A filter that is part of the query cannot be forgotten by a later caller, because forgetting it returns nothing rather than everything.

Concretely: the caller's Club grants enter the persistence call as a parameter, and no repository method exposes a way to fetch a Club-owned resource without one.

### The privacy boundary

- A **Student** sees counts, never lists. How many are enrolled, how many are waiting, their own position in the queue — never who the others are.
- **Form answers are visible only to the owning Club's Officers.** Not to other Students, and not to University Admins, whose legitimate interest is aggregate.
- **Attendance is visible to the owning Club's Officers** and, as a number, on the University Admin's dashboard.
- A Student always sees their own registrations, answers, Waitlist positions and attendance in full.

### Authentication mechanism

Deferred to [the technical baseline](12-lock-core-technical-baseline.md) as a technical choice. This decision binds it only in that the authenticated principal must carry, or allow cheap resolution of, the caller's Club grants — every scoped query needs them on every request.

### Testing obligation

**Every ownership rule in the matrix above requires a negative test**: a Club Officer of Club A attempting the operation on Club B's resource and receiving not-found. An ownership rule with only a positive test is an ownership rule that has not been shown to exist, and the implementation Issues must treat the negative case as part of the feature rather than as extra coverage.
