# Prototype the student registration and check-in experience

Type: prototype
Status: resolved
Blocked by: 05, 07

## Question

What does a student actually see, on a phone, from finding an Event to being marked present?

Build a cheap, throwaway prototype — not production code — covering the moments where the design either works or does not:

- The Event page: what makes a student decide to register, and where capacity and Waitlist position are shown.
- Filling a custom registration form whose fields the frontend did not know about at build time.
- The state of being on the Waitlist, and what an auto-promotion looks like when it arrives.
- The check-in moment at the door: scanning, the rotating code, and every failure the door produces — wrong event, already checked in, expired token, no signal.

The prototype is the artefact to react to; link it from this ticket and store it in `docs/planning/prototypes/`. Its purpose is to expose flows that read badly before they are specified as Issues, not to settle visual design.

## Answer

Prototype: [`student-registration-checkin-prototype.html`](student-registration-checkin-prototype.html) — a mobile state gallery covering all five Event Phases, the custom form including its validation and lost-the-race path, the waitlist and promotion states, and all six door outcomes.

### What it exposed

**Promotion is silent, and that is a real hole.** Notifications are out of scope, so a Student promoted the night before an Event learns about it only if they happen to open the page. Three options were weighed: accept it; add an in-app inbox; or reinstate the confirmation step that the registration decision deliberately rejected.

**Decision: accept the silence, but make the Student's own event list carry the signal.** Each `enrolled` entry now records `via: DIRECT | PROMOTED` and the time, so a promoted Event shows a "You were on the waitlist — you're in" badge whenever the Student next opens it. This costs one extra field on a write that already happens, needs no notification infrastructure, and simultaneously gives the officer console its route-in column. [The registration decision is amended accordingly](../../adr/04-define-registration-capacity-and-waitlist.md). A real in-app inbox is Future Work; the confirmation step stays rejected.

**The lost-the-race screen must preserve the Student's answers.** Filling a form and losing the last Seat is the worst moment in the flow, and discarding the answers turns it into a punishment. Answers are held client-side and carried into the waitlist join. They are not persisted until a Seat exists, so no Registration document is created for a queued Student — the existing decisions stand.

**Five of the six door states are failures.** The door is the highest-stress surface in the product, with a queue of people behind the Student. Two consequences: "code expired" must read as a normal retry rather than an error, since it is the most common outcome and the proof the mechanism works; and the no-signal state must name the manual override on screen, because with no offline queue the only way through is to ask a human.

**"Not on the roster" needs kind wording and a human to point at.** A waitlisted Student who turns up hopeful is a real and sympathetic case, and a bare refusal is the wrong answer.

### Left open for the implementation Issues

Whether "12 seats left" or "28 of 40 taken" leads the Event page — the prototype currently shows both, scarcity as a pill and the fraction as a caption. Whether an expired code triggers an automatic re-scan instead of a button.
