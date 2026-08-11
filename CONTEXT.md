# CampusHub

Campus club events, from signup to the door.

This glossary is the project's ubiquitous language. It holds meaning only — no implementation details, no decisions. Decisions live in [`docs/adr/`](docs/adr/), and scope lives in [`docs/planning/map.md`](docs/planning/map.md).

## Language

**Event**:
A single occasion published by a Club — a talk, a social, a competition heat — that Students can register for and attend. It owns its Capacity, its Seat Ledger and its own registration form.
_Avoid_: Activity, session, booking

**Club**:
A campus society that publishes Events. It is the unit of ownership: a Club Officer's authority extends to their own Club's Events and no further.
_Avoid_: Group, organisation, team

**Student**:
A person who registers for Events and attends them. Students never administer anything.
_Avoid_: User, member, attendee

**Club Officer**:
A Student trusted to publish and run one Club's Events. The role is held per Club, not globally.
_Avoid_: Admin, organiser, owner

**Status**:
The part of an Event's lifecycle that a person decides and the system stores: it is a Draft, it is Published, or it is Cancelled. Nothing else about an Event's progress is stored.
_Avoid_: State, stage — those suggest the derived Phase

**Phase**:
Where an Event stands right now, worked out on demand from its Status, its timestamps and its Seat Ledger. A Phase is never written down, so it can never disagree with the facts it comes from.
_Avoid_: Status, state, step

**Registration Window**:
The period during which an Event accepts new registrations. Closing registration early means moving the end of the Window, not entering a different Status.
_Avoid_: Registration period, open state

**Cancellation**:
A Club Officer or University Admin ending a Published Event before it happens. It freezes the Seat Ledger rather than erasing it, releases the Venue Slot, and cannot be undone.
_Avoid_: Withdrawal — that is a Student leaving; deletion

**University Admin**:
The campus-wide role that manages Venues and reads across every Club. University Admins do not approve Events.
_Avoid_: Superuser, staff, moderator

**Capacity**:
The maximum number of Students an Event can enrol. It is a property of the Event, fixed by the Club Officer.
_Avoid_: Limit, quota, size

**Seat**:
One unit of an Event's Capacity, held by exactly one enrolled Student. Seats are the only contended resource in the system.
_Avoid_: Ticket, place, slot — "Slot" means a Venue time period

**Seat Ledger**:
The authoritative record of who holds an Event's Seats and who is queued for one. It is the single point at which concurrent registration is resolved, and it is always read and changed as one whole.
_Avoid_: Attendee list, roster — "Roster" is the frozen form of this

**Enrolled**:
The state of a Student who holds a Seat. There is no intermediate held, pending or unconfirmed state: a Student is enrolled or is not.
_Avoid_: Confirmed, accepted, booked

**Registration**:
A Student's answers to an Event's own registration form, recorded once they are Enrolled. It carries the answers, never the Seat.
_Avoid_: Signup, booking, enrolment record

**Waitlist**:
The ordered queue of Students who wanted a Seat when none was free. It is first-in-first-out, survives the Event, and is evidence of demand rather than a promise of admission.
_Avoid_: Queue, standby, reserve list

**Promotion**:
The moment a Waitlist head becomes Enrolled because a Seat was freed. It is automatic, immediate, and requires nothing of the Student being promoted.
_Avoid_: Upgrade, offer, invitation

**Withdrawal**:
A Student giving up their Seat or leaving the Waitlist before the Event starts. Withdrawing from a Seat causes a Promotion; withdrawing from the Waitlist does not.
_Avoid_: Cancellation — that word belongs to the Club Officer cancelling an Event

**Roster**:
The Seat Ledger frozen at the moment the Event starts. It is what the door checks a Student against, and it does not change afterwards.
_Avoid_: Attendance — the Roster says who may attend, not who did

**Venue**:
A bookable campus space. An Event may occupy one for a Slot.
_Avoid_: Room, location, place

**Slot**:
A period of time for which a Venue is occupied by one Event. Two Events can never hold overlapping Slots in the same Venue.
_Avoid_: Booking, reservation, seat
