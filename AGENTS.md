## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `Jamiedz999/campushub`. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical triage roles use `needs-triage`, `needs-info`, `ready`, and `wontfix`; both implementation-ready roles map to `ready`. See `docs/agents/triage-labels.md`.

### Domain docs

Domain docs use a single-context layout with `CONTEXT.md` and `docs/adr/` at the repository root. See `docs/agents/domain.md`.

### Commit and PR conventions

Do not put AI attribution in a commit message or a PR body. This bans two things: the `Co-Authored-By: Claude ...` trailer, and the "🤖 Generated with [Claude Code]" footer. A commit records one author, and that author is the repository owner.

The trailer is not cheap to remove later. It is public text on every commit page, and GitHub keeps a copy on `refs/pull/*` that a force-push cannot reach. Keep it out in the first place.
