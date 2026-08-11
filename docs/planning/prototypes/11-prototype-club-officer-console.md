# Prototype the club officer console

Type: prototype
Status: resolved
Blocked by: 04, 06, 09

## Question

What does a club officer's workspace look like, from creating an Event to reading its attendance?

Build a cheap, throwaway prototype covering:

- Creating an Event: the lifecycle states made visible, and where the custom form builder sits without dominating the flow.
- Booking a Venue slot, and what a collision looks like when the atomic write loses.
- Watching registrations and the Waitlist fill, including a live count during registration.
- Running the door: displaying the rotating code and watching attendance arrive.
- Reading the dashboard afterwards, and exporting form answers.

Desktop-first, unlike the student surface. Link the prototype from this ticket and store it in `docs/planning/prototypes/`.

## Answer

Prototype: [`club-officer-console-prototype.html`](club-officer-console-prototype.html) — a desktop state gallery covering Event creation and what locks when, Venue booking including a lost race, the live registration view, the door screen with manual override, and the club dashboard.

### What it exposed

**Raising capacity is a destructive-feeling action and must be explained before it is clicked.** Going from 40 to 50 promotes waiting Students immediately and irreversibly. The console must say "this will admit 4 waiting students now" rather than presenting a bare number field.

**The editability rules only make sense shown as a table.** Title and description free, timestamps and venue until start, capacity raise-only, form locked once anyone registers, publish irreversible. Scattered across a form these read as arbitrary; collected in one place they read as a policy.

**The "route in" column justified itself here independently.** An officer looking at a full event wants to know how many people came off the queue — the same `promotedCount` and `via` fields the dashboard and the student prototype separately demanded.

**The unmet-demand table is what makes the dashboard worth building.** "Night Bouldering: capacity 25, 19 never got in" is an argument for a bigger room next term. Charts alone would have been decoration; this one table is the reason an officer would open the page twice.

### Left open for the implementation Issues

Whether a partially completed reschedule — the club holding both the old and the new Slot, which the acquire-before-release rule permits — is surfaced to the officer or cleaned silently on next view. Whether four KPIs is the right density for the club view or two belong only to the University Admin's cross-club view.
