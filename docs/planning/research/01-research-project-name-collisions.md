# Research project name collisions

Type: research
Status: resolved
Blocked by: none

## Question

Is `CampusHub` safe to use as this project's public name, and if not, which alternatives are?

Campus event management is one of the most crowded final-year-project themes in existence, so a same-name, same-function, same-stack collision is likely and would make the repository look derivative to anyone who searches for it.

Screen for collisions across GitHub repositories, npm and PyPI package names, product and SaaS names, and app stores. For each collision record the name, what it does, its stack, its popularity, and whether it is a live product or an abandoned coursework repo. Apply the standard already used on Delivery Glance: exact name reuse is acceptable when existing projects differ materially in **either** function **or** language/technology; avoid a same-name project with substantially the same function and stack.

Deliver a shortlist of three names that pass the screen, each with a one-line positioning sentence. Prefer a direct English name built from two familiar words. Avoid names that imply a university's official system, a student information system, or a general-purpose social network.

Write findings to `docs/planning/research/` and link them from this ticket.

## Answer

Full evidence: [`docs/planning/research/01-project-name-collisions.md`](01-project-name-collisions.md).

**`CampusHub` fails the screen and must not be used.** GitHub returns 944 repositories with `campushub` in the name. Two of them close the door between them: [`is-Xiaoen/CampusHub`](https://github.com/is-Xiaoen/CampusHub) (29★, Go/go-zero, active) is a campus event publishing, registration and check-in platform with a **rotating TOTP check-in code** — the same function down to the same mechanic; and [`HEYWEEN/CampusHub`](https://github.com/HEYWEEN/CampusHub) (5★, active) is a university software-engineering course project on **Java 17 + Spring Boot 3.5 + React 19 + TypeScript + Vite + TanStack Query + Zustand + GitHub Actions**, holding the `com.campushub` base package. The Delivery Glance standard is written for one colliding project; here the function twin and the stack twin are different repositories sharing the name, so the derivative reading occurs anyway. On top of that: four live commercial products (campushub.io, mycampushub.com, campushub.my, HootBoard CampusHub), five app-store listings — the [CampusHub.io iOS app](https://apps.apple.com/us/app/campushub-io/id1566899268) advertises a campus activity schedule with RSVP — and `github.com/campushub` is taken. The name also leans toward the official-institution and student-information-system readings this ticket excludes. The placeholder directory `campushub/` is therefore renamed at ticket 02.

Three names pass the screen. All three are unclaimed on npm, PyPI, GitHub handles and Docker Hub.

1. **`ClubRoster`** — a campus club publishes an event, students take the seats, and the roster it builds is the same list the door scans on the night.
   Only one same-name repository exists anywhere: a dormant 1★ CSS page for a soccer team (2020). `clubroster.com` is a live nightlife performer directory — different function, not software. `clubroster.dev` and `clubroster.io` are free. Watch-out: "roster" can drift toward membership management, which is adjacent to the excluded SIS reading.

2. **`SignupDesk`** — registration and the check-in desk in one system: students sign up for a club event, the waitlist promotes itself when seats free up, and a rotating QR code at the door turns signups into attendance.
   The cleanest of the three: zero GitHub repositories, no product on any surface. `signupdesk.com` is registered but parked and listed for sale; `signupdesk.io` and `signupdesk.app` are free. Watch-out: `-Desk` reads as helpdesk/ITSM (Zendesk, Freshdesk). It is nonetheless the only shortlisted name covering both the signup and the door.

3. **`SeatQueue`** — seats are finite, so the queue is the product: capacity, an auto-promoting waitlist and a non-colliding venue slot, all resolved by atomic single-document updates.
   Zero GitHub repositories, no product. `seatqueue.com` is registered but a parked link-farm page; `seatqueue.app` is free. Watch-out: it under-describes QR check-in and the attendance dashboard, but it points most directly at the project's headline engineering story.

Held in reserve, clean on every surface but weaker English: `ClubSeat`, `SeatDesk`, `SeatBoard`, `VenueRoll`, `SeatRoll`, `DoorRoll`. Rejected for cause: `SeatHold` (0★ but Java **and** an event seat-reservation system — fails outright), `EventRoll` (active JS event-management system with attendance tracking), `ClubBoard` (Cornell Java campus-clubs project), `ClubDoor` (68★ unrelated bot dominates the name), `ClubDeck` (reads as Clubhouse tooling), `DoorCount` (an established retail footfall metric).

Choosing among the three, and writing the final positioning sentence, is ticket 02's decision.
