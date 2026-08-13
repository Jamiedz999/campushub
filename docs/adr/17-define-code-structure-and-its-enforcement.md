# Define the code structure and what enforces it

Type: grilling
Status: resolved
Blocked by: 12, 15

## Question

What is inside a backend module, what is inside `web/src`, and what stops either from drifting?

## Answer

### The gap this closes is enforcement, not layout

[The technical baseline](../planning/implementation/TECHNICAL-BASELINE.md) already settles the module boundaries, and it states plainly why they matter:

> **A module owns whole documents. Two modules never write the same document.** This is the rule that keeps the atomicity argument true: if the Seat Ledger could be written from two places, the guarantee that every race is resolved by one guarded write would depend on **discipline instead of structure**.

That sentence is correct, and then the baseline hands the rule to discipline anyway — it is written down and nothing checks it. **This is the only load-bearing rule in the project left in that state.** Authorization is enforced by scoping the query rather than checking after reading. The Roster freeze is carried in the guard filter rather than in prose. Indexes are owned by Mongock rather than inferred from annotations. Each of those is the same argument: a rule that depends on the next caller remembering will be broken by the first caller who does not, silently.

So the decision here is less about which folders exist than about what fails the build when the wrong import is written.

### The backend: one public type per module

A module is a direct sub-package of `com.campushub`. Inside it:

```text
com.campushub
├── CampusHubApplication.java
├── shared/                     Clock, identifiers, error primitives — never business logic
├── system/
└── event/
    ├── EventModule.java        the module's only public type
    ├── domain/                 the Event document, Phase derivation, Seat Ledger invariants
    ├── persistence/            MongoTemplate access
    ├── web/                    controller and DTOs
    └── internal/               everything else
```

**Everything except the `*Module` interface is package-private.** Java's package-private access is exactly the tool for this: a peer module reaching past the interface is a compile error rather than something a reviewer might catch. `event` is subdivided further than the others because the Event document is large, which the baseline already anticipated.

### Enforcement: Spring Modulith for boundaries, ArchUnit for ownership

**Spring Modulith 2.1**, whose release train pairs with Spring Boot 4.1, verifies module boundaries with a single test — `ApplicationModules.of(CampusHubApplication.class).verify()`. It also **generates the module documentation and component diagram from the code**, which matters beyond convenience: a hand-drawn architecture diagram is a derived value maintained by hand, and this project has already rejected that pattern twice, for Phase and for stored state. The generated diagram cannot disagree with the packages it describes.

Modulith does not know what MongoDB is, so the two rules that actually carry the concurrency argument are **custom ArchUnit rules**, running in the same suite:

1. **No type outside a module's `persistence` package may reference `MongoTemplate`.**
2. **No module may reference another module's document types.**

Together these are what turn "two modules never write the same document" from a sentence into a build failure. Modulith is itself built on ArchUnit, so this is one tool used at two levels rather than two tools.

Rejected: **ArchUnit alone**, hand-writing the boundary rules Modulith already ships. It was the initial recommendation, on the belief that Modulith's release train would lag Spring Boot 4.1. That belief was wrong — 2.1 GA released against Boot 4.1 — and with the version risk gone, hand-maintaining rules a maintained library provides is work with no return, and it forgoes the generated documentation.

Rejected: **Maven multi-module**, one artefact per business module. It enforces the same boundaries through the build rather than through tests, at the cost of a reactor build, cross-module refactoring friction, and a repository shape disproportionate to one deployable. Package-private plus a verification test buys the same guarantee at a fraction of the ceremony — the proportionate-engineering position this project has taken since BookInn.

### Where tests live, and the conflict that resolves

Two existing decisions pull in opposite directions. The module rule wants a test beside the code it covers. [The hardening Issue](https://github.com/Jamiedz999/campushub/issues/17) wants the four concurrency tests **gathered in one place**, because together they are the project's central claim and a reader should be able to run them as a set.

**Resolved with JUnit tags rather than directories.** Concurrency tests live beside their module and carry `@Tag("concurrency")`, so they are both local to their owner and runnable as one suite. A directory would have forced a choice; a tag does not.

Alongside it, the standard Maven split, which the baseline implies but never states: **`*Test` runs under Surefire, `*IntegrationTest` under Failsafe.** Testcontainers costs seconds per class, and mixing the two means every local run pays for the slow ones.

### The frontend: features that cannot see each other

```text
web/src
├── app/            router, providers, entry
├── features/
│   ├── events/     api/ components/ hooks/ types.ts
│   ├── registration/
│   ├── checkin/
│   └── dashboard/
├── components/     cross-feature presentational UI
├── lib/            the axios instance, the query client, error normalisation
└── types/
```

**A feature may not import from another feature.** Shared code moves down into `lib` or `components`, or the two are composed at the route level in `app`. This is the same rule as the backend's, one layer up, and it is the rule that decides whether `web/src` is still navigable in Sprint 5.

**Enforced by ESLint `import/no-restricted-paths`**, failing `npm run check`. Without it the convention lasts exactly as long as the first afternoon someone is in a hurry — which is the whole argument of this ADR applied to the half of the codebase where it is easiest to ignore.

Rejected: **Feature-Sliced Design**, whose six layers are a real answer to a problem this project does not have at four features. Rejected: **`components/` plus `pages/` only**, which has no seam at all and starts to blur the moment registration needs something check-in also wants — around Sprint 3.

### What this costs

One dependency, one lint rule, and roughly three tests. The return is that every structural claim this project makes in an interview is checkable by running the build, which is the same standard the concurrency claims are already held to.
