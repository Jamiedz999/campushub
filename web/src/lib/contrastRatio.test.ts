import { describe, expect, it } from "vitest";
import { contrastRatio } from "./contrastRatio";

describe("contrastRatio", () => {
  it("gives black on white the maximum WCAG can express", () => {
    expect(contrastRatio("#000000", "#ffffff")).toBeCloseTo(21, 2);
  });

  it("gives a colour against itself the minimum", () => {
    expect(contrastRatio("#3b7dd8", "#3b7dd8")).toBeCloseTo(1, 5);
  });

  it("does not care which of the pair is the foreground", () => {
    expect(contrastRatio("#1f2933", "#ffffff")).toBeCloseTo(contrastRatio("#ffffff", "#1f2933"), 10);
  });

  it("matches the ratio WCAG quotes for its own worked example", () => {
    // #767676 on white is the canonical "exactly 4.5:1" pair from the WCAG contrast guidance —
    // the boundary the AA rule is written around, so an implementation that is subtly wrong shows
    // up here rather than only on colours nobody checks by hand.
    expect(contrastRatio("#767676", "#ffffff")).toBeCloseTo(4.54, 2);
  });

  it("reads three-digit hex the same as its six-digit spelling", () => {
    expect(contrastRatio("#fff", "#000")).toBeCloseTo(contrastRatio("#ffffff", "#000000"), 10);
  });

  it("ignores the case of the hex digits", () => {
    expect(contrastRatio("#AABBCC", "#ffffff")).toBeCloseTo(contrastRatio("#aabbcc", "#ffffff"), 10);
  });

  it("refuses a colour it cannot read rather than guessing a ratio", () => {
    expect(() => contrastRatio("rebeccapurple", "#ffffff")).toThrow(/hex/i);
    expect(() => contrastRatio("#12345", "#ffffff")).toThrow(/hex/i);
  });
});
