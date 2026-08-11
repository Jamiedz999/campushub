# Project name collision screen

Research for ticket [`01-research-project-name-collisions`](01-research-project-name-collisions.md). Screened 2026-08-10.

## Screening standard

Carried over from Delivery Glance: **exact name reuse is acceptable when the existing project differs materially in either function or in language/technology.** A same-name project with substantially the same function *and* the same stack is disqualifying, because it makes this repository read as derivative to anyone who searches the name.

Two additions applied here, from the ticket:

- The name must not imply a university's official system, a student information system, or a general-purpose social network.
- Prefer a direct English name built from two familiar words.

Surfaces screened for every name: GitHub repository names, the npm registry, PyPI, live products and SaaS, the Apple App Store and Google Play, and (cheaply) domain and handle availability.

### Method and sources

- GitHub: `GET https://api.github.com/search/repositories?q=<name>+in:name&sort=stars` — repository count, stars, primary language, last push date, archived flag. READMEs read directly from `raw.githubusercontent.com`.
- npm: `GET https://registry.npmjs.org/<name>` — HTTP 404 means the package name is unclaimed.
- PyPI: `GET https://pypi.org/pypi/<name>/json` — HTTP 404 means unclaimed.
- Domains: RDAP via `https://rdap.org/domain/<domain>` — HTTP 404 means not registered, 200 means registered. Registered domains were then fetched over HTTP to see whether they serve a live product or a parking page.
- Handles: `GET https://api.github.com/users/<name>` and `https://hub.docker.com/v2/users/<name>/`.

---

## 1. `CampusHub` — the incumbent placeholder

### GitHub

`campushub in:name` returns **944 repositories**; the looser `campus-hub` query returns 2,650. The name is not merely taken, it is a genre label. The top of the distribution:

| Repository | Stars | Stack | Function | State |
|---|---|---|---|---|
| [`is-Xiaoen/CampusHub`](https://github.com/is-Xiaoen/CampusHub) | 29 | Go 1.24, go-zero microservices, MySQL, Redis, Elasticsearch, DTM (SAGA), Watermill, Jaeger | **Campus event publishing, registration and check-in.** Event CRUD with a table-driven state machine (draft → review → published → running → ended), multi-criteria filtering with deep-pagination optimisation, registration and cancellation, **electronic tickets validated by a rotating TOTP code**, idempotent check-in, student verification | Live, last push 2026-03-25 |
| [`Victor-Kipruto-Rop/CAMPUSHUB`](https://github.com/Victor-Kipruto-Rop/CAMPUSHUB) | 26 | Python | Centralised campus management: student collaboration, communication, resource sharing | Live, last push 2026-03-27 |
| [`Victor-Kipruto-Rop/CampusHub-Mobile`](https://github.com/Victor-Kipruto-Rop/CampusHub-Mobile) | 25 | TypeScript | Mobile companion to the above | Live, 2026-03-27 |
| [`autopoet/CampusHub`](https://github.com/autopoet/CampusHub) | 9 | Vue 3, Vite | Practice project exploring Web Workers and SPA state isolation | Live, 2026-04-06 |
| [`Fly-Xu-cmd/CampusHub_frontend`](https://github.com/Fly-Xu-cmd/CampusHub_frontend) | 7 | uni-app, Vue 3, TypeScript, Vite | Cross-platform campus app | Live, 2026-03-25 |
| [`HEYWEEN/CampusHub`](https://github.com/HEYWEEN/CampusHub) | 5 | **Java 17, Spring Boot 3.5.3, React 19, TypeScript 5.9, Vite, TanStack Query 5, Zustand 5, GitHub Actions CI, JUnit 5 + Mockito + ArchUnit, MySQL 8 + Flyway.** Base package `com.campushub` | O2O campus mutual-aid platform: errands, second-hand trading with multi-round haggling, tutoring, team matching, DM, credit score, AI assistant. Modular monolith | Live coursework, Nanjing University *Software Engineering and Computing II*, 2025–2026, last push 2026-06-15 |
| [`JaeAeich/CampusHub`](https://github.com/JaeAeich/CampusHub) | 5 | React + TypeScript, ShadCN, Tailwind, Vite; Flask backend | "All-in-one solution for your college needs" | Effectively abandoned, last push 2024-05-17 |
| [`ivandress/IPPS-DIPLOMADO-CURSO3-campushub-api`](https://github.com/ivandress/IPPS-DIPLOMADO-CURSO3-campushub-api) | 3 | Node + Express + **MongoDB** (frontend repo is React + Vite) | Course platform: courses, enrolment, students, Google login | Live coursework, 2026-07-09 |

Two of these matter individually, and together they close the door:

- **`is-Xiaoen/CampusHub` is a function twin.** Campus events, registration, check-in, and specifically a *rotating one-time code* for check-in — the same mechanic this project plans. It is the highest-starred `CampusHub` on GitHub and it is active. It passes the standard only on stack (Go microservices vs Java monolith).
- **`HEYWEEN/CampusHub` is a stack twin.** Java 17 + Spring Boot 3.5 + React 19 + TypeScript + Vite + TanStack Query + Zustand + GitHub Actions CI, presented as a university software-engineering course project, with base package `com.campushub` — the exact package root this project would claim. It passes the standard only on function (O2O errands and trading, not events), and only because MongoDB replaces its MySQL + JPA.

The standard is written for a *single* colliding project. Here the function collision and the stack collision are different repositories with the same name, so any recruiter searching `CampusHub` finds one project that does what this one does and another that is built the way this one is built — both of them coursework-flavoured. The derivative reading the standard exists to prevent happens anyway.

### Packages

- npm `campushub` — unclaimed (404).
- npm `campus-hub` — unclaimed (404).
- PyPI `campushub` / `campus-hub` — unclaimed (404).

Package registries are clean. They are the only surface that is.

### Products and SaaS

| Product | What it does | State |
|---|---|---|
| [campushub.io](https://www.campushub.io/) | School communication platform — mobile apps and digital signage broadcasting school information to students, staff and parents | Live commercial product (Alpine Media Technology) |
| [mycampushub.com](https://www.mycampushub.com/) | US college commerce and community: secondhand marketplace, student businesses, **event tickets**, filtered per campus | Live |
| [campushub.my](https://campushub.my/) | Malaysian campus SaaS ecosystem: parcel management, printing, 3D printing, reward points | Live |
| [HootBoard CampusHub](https://about.hootboard.com/education/enhancing-campus-life-hootboard-campushub/) | Interactive campus kiosks and boards | Live commercial product |

### App stores

| Listing | What it does |
|---|---|
| [CampusHub.io (iOS)](https://apps.apple.com/us/app/campushub-io/id1566899268) | School community app with a **searchable activity schedule, upcoming events and RSVP** |
| [CampusHub — corsi eventi e test (iOS)](https://apps.apple.com/us/app/campushub/id6739215636) | Italian post-secondary orientation: universities, courses, entrance-test simulation |
| [Campus Hub (iOS)](https://apps.apple.com/us/app/campus-hub/id6499357140) | Campus passes in one place |
| [CampusHub (Google Play, `com.sequspace.campushub`)](https://play.google.com/store/apps/details?id=com.sequspace.campushub) | All-in-one campus management: admissions, attendance, examinations, fees, schedules |
| [CampusHub (Google Play, `io.campushub`)](https://play.google.com/store/apps/details?id=io.campushub) | Android build of campushub.io |

The CampusHub.io iOS listing is a *live commercial product* whose described function includes campus event schedules and RSVP. That is a second function collision, this time from a real company rather than coursework.

### Handles and domains

- `github.com/campushub` — **taken** (200).
- `campushub.com` — registered.

### Verdict on `CampusHub`

**Fails the screen. Do not use it.**

The failure is not a single disqualifying twin but the density of the field: 944 same-name repositories, an active 29-star Go project that implements the same features down to the rotating check-in code, an active Java + Spring Boot + React + TypeScript course project holding the `com.campushub` package root, several live commercial products, and five app-store listings — one of which advertises campus events and RSVP. The name carries no signal, and it also leans toward the two things the ticket rules out: `campushub.com`-class products are largely official-institution systems and student-information systems.

The placeholder directory `campushub/` should be renamed once ticket 02 picks a name.

---

## 2. Candidate screen

Names were generated to satisfy "two familiar English words", to name what the system actually does (a club's event, a seat, a queue, a check-in desk, an attendance roster), and to avoid the words that trigger the excluded readings — no `Campus`, no `Uni`, no `Student`, no `Portal`, no `Connect`, no `Social`.

### Rejected candidates and why

| Candidate | Finding | Outcome |
|---|---|---|
| `EventRoll` | 13 GitHub repos. [`runer0101/EventRoll`](https://github.com/runer0101/EventRoll) (1★, JavaScript, active 2026-06-01) is "web-based event management system with RBAC, **real-time attendance tracking**, bulk guest import" — a direct function collision, and the only differentiator is the stack | Rejected — same function, and the name is generic enough that the differentiator is thin |
| `ClubDeck` | [`clubdeck/clubdeck.github.io`](https://github.com/clubdeck/clubdeck.github.io) at 33★; ClubDeck is a known third-party desktop client for Clubhouse. Function differs, but the name reads as "Clubhouse tooling" | Rejected on connotation |
| `ClubDoor` | [`TiraelSedai/ClubDoorman`](https://github.com/TiraelSedai/ClubDoorman), 68★, C#, active 2026-08-08 (Telegram anti-spam bot). Function differs, but it is the loudest thing under the name | Rejected — the highest-signal neighbour is unrelated and more popular |
| `DoorCount` | 18 GitHub repos, all Arduino/IoT people-counters; "door count" is also an established retail footfall metric | Rejected — the name means something else in industry, and covers only check-in |
| `SeatHold` | [`Rayito391/SeatHold`](https://github.com/Rayito391/SeatHold), 0★, **Java**, active 2026-01-07: "event seat reservation system powered by Redis" — same function *and* same language | Rejected — fails the standard outright, despite zero stars |
| `ClubBoard` | 8 repos including [`Cornell-ClubBoard/cornell-club-board-backend`](https://github.com/Cornell-ClubBoard/cornell-club-board-backend) (Java, campus clubs). Also `clubboardresort.kr` | Rejected — Java + campus clubs is too close |
| `ClubSeat`, `SeatDesk`, `SeatBoard`, `VenueRoll`, `SeatRoll`, `DoorRoll` | All clean on every surface (0 GitHub repos in name, npm and PyPI unclaimed) | Held in reserve — clean, but weaker English than the three below |
| `EventRoster` | 2 repos, both empty of description and dormant/near-dormant | Passes, but `Event` is the most generic word available and search results for it are unusable |

### Shortlist

#### 1. `ClubRoster`

*A campus club publishes an event, students take the seats, and the roster it builds is the same list the door scans on the night.*

| Surface | Finding |
|---|---|
| GitHub | **1 repository** in the entire index: [`dungquoctrinh/clubroster`](https://github.com/dungquoctrinh/clubroster), 1★, CSS, last push 2020-09-04, described as "side project for the soccer team". Abandoned; different function (a sports team page); different stack (static CSS) |
| npm | `clubroster` unclaimed (404) |
| PyPI | `clubroster` unclaimed (404) |
| Products / SaaS | No product named ClubRoster. Adjacent club-management SaaS exists under other names ([Clubspot](https://theclubspot.com/), [WildApricot](https://www.wildapricot.com/who-we-serve/clubs), [Rostered](https://rostered.app/sports-club-management-software/), [SportsEngine](https://www.sportsengine.com/organizations/clubs-and-associations/)), none using this name |
| App stores | No ClubRoster listing found |
| Handles | `github.com/clubroster` free; Docker Hub `clubroster` free |
| Domains | `clubroster.com` **registered and live** — "Club Roster – Live Performer Directory & Club Discovery", a nightlife performer directory, materially different function and not software. `clubroster.dev` and `clubroster.io` free |

**Passes.** The only same-name repository is a dormant 2020 static page for a soccer team.

Risk to note for ticket 02: "roster" can be read as membership management, which is adjacent to the student-information-system reading the ticket excludes. The positioning sentence has to do the work of tying the roster to a single event rather than to the club's membership.

#### 2. `SignupDesk`

*Registration and the check-in desk in one system: students sign up for a club event, the waitlist promotes itself when seats free up, and a rotating QR code at the door turns signups into attendance.*

| Surface | Finding |
|---|---|
| GitHub | **0 repositories** with `signupdesk` in the name |
| npm | `signupdesk` unclaimed (404) |
| PyPI | `signupdesk` unclaimed (404) |
| Products / SaaS | No product named SignupDesk. Neighbours are distinct names in an adjacent space: [SignUp.com](https://signup.com/), [SignUpGenius](https://www.signupgenius.com/), [SignUp Software](https://www.signupsoftware.com/) (Dynamics 365 AP automation), SignDesk (eSign) |
| App stores | No SignupDesk listing found |
| Handles | `github.com/signupdesk` free; Docker Hub `signupdesk` free |
| Domains | `signupdesk.com` **registered but parked** — served as "SignUpDesk.com — Premium Domain For Sale" via Atom, i.e. no product behind it. `signupdesk.io` and `signupdesk.app` free |

**Passes cleanly — the cleanest of the three.** Nothing is shipping under this name on any screened surface.

Risk to note for ticket 02: `-Desk` is a strong helpdesk/ITSM convention (Zendesk, Freshdesk, SupportDesk), so the name may read as a ticketing tool on first glance. Set against that, it is the only shortlisted name that captures both halves of the product — the signup and the door.

#### 3. `SeatQueue`

*Seats are finite, so the queue is the product: capacity, an auto-promoting waitlist and a non-colliding venue slot, all resolved by atomic single-document updates.*

| Surface | Finding |
|---|---|
| GitHub | **0 repositories** with `seatqueue` in the name |
| npm | `seatqueue` unclaimed (404) |
| PyPI | `seatqueue` unclaimed (404) |
| Products / SaaS | No product named SeatQueue. Neighbours use different names: [ScanQueue](https://scanqueue.com/) (virtual queue and waitlist for walk-in businesses), [QueuePad](https://apps.apple.com/us/app/queuepad-for-customer-waitlist/id973842987), [Seatlab](https://seatlab.com/) (venue seat mapping), SeatGeek (ticket resale) |
| App stores | No SeatQueue listing found |
| Handles | `github.com/seatqueue` free; Docker Hub `seatqueue` free |
| Domains | `seatqueue.com` registered but **parked** (a link-farm "Resources and Information" page, ~4 KB, no product). `seatqueue.app` free |

**Passes.** Zero code-hosting or package presence anywhere.

Risk to note for ticket 02: the name foregrounds capacity and the waitlist and says nothing about QR check-in or the attendance dashboard, so it under-describes roughly half of Core. It is, however, the name that points most directly at the project's headline engineering story — the race condition contained inside one document.

---

## Summary table

| Name | GitHub repos in name | Worst same-name collision | npm | PyPI | Live product | Verdict |
|---|---|---|---|---|---|---|
| `CampusHub` | 944 | 29★ Go campus-events platform with rotating check-in codes; separately, a 5★ Java + Spring Boot + React + TS course project on `com.campushub` | free | free | four, plus five app-store listings | **Fail** |
| `ClubRoster` | 1 | 1★ dormant 2020 CSS page for a soccer team | free | free | a nightlife performer directory at `clubroster.com` | **Pass** |
| `SignupDesk` | 0 | none | free | free | none (`.com` parked, for sale) | **Pass** |
| `SeatQueue` | 0 | none | free | free | none (`.com` parked) | **Pass** |

## What this research does not decide

The final name is a human decision held by ticket 02, together with its one-line positioning sentence, the repository name, the Java base package, the Docker image name and the deployed hostname. This file only establishes that `CampusHub` cannot be that name and that these three can.
