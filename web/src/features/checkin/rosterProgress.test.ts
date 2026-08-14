import { describe, expect, it } from "vitest";
import { rosterProgress } from "./rosterProgress";
import type { RosterEntry } from "./types";

function entry(studentId: string, method: RosterEntry["method"]): RosterEntry {
  return {
    studentId,
    displayName: studentId,
    at: method === null ? null : "2026-03-20T18:04:00Z",
    method,
  };
}

describe("rosterProgress", () => {
  it("counts every held Seat, whether or not that Student is in the room", () => {
    const progress = rosterProgress([
      entry("a", "SCANNED"),
      entry("b", "MANUAL"),
      entry("c", null),
    ]);

    expect(progress).toEqual({ enrolled: 3, attended: 2, scanned: 1, manual: 1 });
  });

  it("keeps scanned and manual apart, because a club that is mostly manual has demonstrated less", () => {
    const progress = rosterProgress([entry("a", "MANUAL"), entry("b", "MANUAL")]);

    expect(progress.scanned).toBe(0);
    expect(progress.manual).toBe(2);
    expect(progress.attended).toBe(2);
  });

  it("is all zeroes for an Event nobody has registered for", () => {
    expect(rosterProgress([])).toEqual({ enrolled: 0, attended: 0, scanned: 0, manual: 0 });
  });
});
