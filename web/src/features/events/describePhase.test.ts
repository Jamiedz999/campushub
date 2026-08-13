import { describe, expect, it } from "vitest";
import { describePhase } from "./describePhase";
import type { EventBrowseItem } from "./types";

function item(overrides: Partial<EventBrowseItem>): EventBrowseItem {
  return {
    id: "event-1",
    clubId: "club-1",
    title: "Robotics Night",
    description: "Build a robot",
    phase: "REGISTRATION_OPEN",
    registrationOpensAt: "2026-03-01T00:00:00Z",
    registrationClosesAt: "2026-03-10T00:00:00Z",
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-03-20T02:00:00Z",
    capacity: 40,
    enrolledCount: 28,
    waitlistCount: 0,
    ...overrides,
  };
}

describe("describePhase", () => {
  it("describes Draft", () => {
    expect(describePhase(item({ phase: "DRAFT" }))).toBe("Not visible to Students at all");
  });

  it("describes Scheduled with the registration open date", () => {
    expect(describePhase(item({ phase: "SCHEDULED" }))).toContain("Registration opens on");
  });

  it("describes Registration Open with seats remaining", () => {
    expect(describePhase(item({ phase: "REGISTRATION_OPEN", capacity: 40, enrolledCount: 28 }))).toBe(
      "12 of 40 seats left",
    );
  });

  it("describes Full with the waitlist count", () => {
    expect(describePhase(item({ phase: "FULL", waitlistCount: 4 }))).toBe(
      "Event full — join the waitlist (4 waiting)",
    );
  });

  it("describes Registration Closed", () => {
    expect(describePhase(item({ phase: "REGISTRATION_CLOSED" }))).toBe("Registration has closed");
  });

  it("describes In Progress", () => {
    expect(describePhase(item({ phase: "IN_PROGRESS" }))).toBe("Happening now");
  });

  it("describes Completed", () => {
    expect(describePhase(item({ phase: "COMPLETED" }))).toBe("This event has ended");
  });

  it("describes Cancelled", () => {
    expect(describePhase(item({ phase: "CANCELLED" }))).toBe("This event was cancelled");
  });
});
