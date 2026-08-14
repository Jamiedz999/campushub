// Node's types are pulled in for this file alone rather than for the whole app project. This is the
// only file under src/ that reads the filesystem, and widening the app's type environment to suit it
// would put `process` and `Buffer` in scope for application code that must never touch either.
/// <reference types="node" />
import { readFileSync } from "node:fs";
import process from "node:process";
import { describe, expect, it } from "vitest";
import { contrastRatio } from "../../lib/contrastRatio";

/**
 * The door screen is projected onto a wall and read from across a room with the lights on. Contrast
 * and type size are therefore correctness there, not polish, and this is where that is enforced.
 *
 * It reads the tokens out of the stylesheet that defines them rather than restating them, so there is
 * one place to change a colour and one test that notices. Nothing here renders a component: a
 * component test would only prove the class name was spelled right, and the thing that can silently go
 * wrong is the value behind it.
 *
 * Read off disk rather than imported, because `import … from "./index.css"` hands back whatever the
 * CSS pipeline made of the file, and what this test is about is the declaration a person wrote.
 */
const STYLESHEET = readFileSync(`${process.cwd()}/src/index.css`, "utf8");

function token(name: string): string {
  const value = new RegExp(`--${name}:\\s*([^;]+);`).exec(STYLESHEET)?.[1];
  if (value === undefined) {
    throw new Error(`the stylesheet no longer declares --${name}`);
  }
  return value.trim();
}

function rem(name: string): number {
  const value = token(name);
  const size = /^([\d.]+)rem$/.exec(value)?.[1];
  if (size === undefined) {
    throw new Error(`--${name} is ${value}; the door screen's sizes are declared in rem so they scale`);
  }
  return Number(size);
}

const SURFACE = "color-door-surface";

describe("the projected door screen's palette", () => {
  // AAA rather than AA. AA is written for a screen an arm's length away in ordinary light; this one
  // is a projector's washed-out output at the back of a lecture room, and the extra headroom is what
  // survives that.
  it.each(["color-door-ink", "color-door-muted", "color-door-good", "color-door-warn"])(
    "%s reaches WCAG AAA against the screen's own background",
    (name) => {
      expect(contrastRatio(token(name), token(SURFACE))).toBeGreaterThanOrEqual(7);
    },
  );

  it("holds its rules and borders to the non-text bar, which they are the only things allowed to use", () => {
    expect(contrastRatio(token("color-door-line"), token(SURFACE))).toBeGreaterThanOrEqual(3);
  });

  // Deliberately not asserted: that the "counting live" green and the "not connected" red are
  // distinguishable from each other. Two colours dark enough to reach AAA on a white wall are close
  // to each other in luminance by construction, and a red/green pair is the one nobody should be
  // reading as a signal anyway. The two states are told apart by their sentences — see
  // OfficerDoorPage.test.tsx — and the colour only ever agrees with the words.
});

describe("the projected door screen's type scale", () => {
  it("makes the attendance count the thing you can read from the back of the room", () => {
    expect(rem("text-door-count")).toBeGreaterThanOrEqual(6);
  });

  it("keeps every other size on the screen well above what a phone would use", () => {
    expect(rem("text-door-title")).toBeGreaterThanOrEqual(2);
    expect(rem("text-door-body")).toBeGreaterThanOrEqual(1.375);
    expect(rem("text-door-caption")).toBeGreaterThanOrEqual(1.125);
  });

  it("keeps the scale in order, so the count is never smaller than the caption under it", () => {
    expect(rem("text-door-count")).toBeGreaterThan(rem("text-door-title"));
    expect(rem("text-door-title")).toBeGreaterThan(rem("text-door-body"));
    expect(rem("text-door-body")).toBeGreaterThan(rem("text-door-caption"));
  });
});
