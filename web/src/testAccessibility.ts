import axe from "axe-core";

/**
 * Runs axe over rendered markup and returns what it found, one readable line per violation.
 *
 * It sits at the root of `src/` beside `testSetup.ts` rather than in a directory of its own, because
 * the documented `web/src` layout is `app/ features/ components/ lib/ types/` and this is not one of
 * those — it is test support, which this project keeps either beside the code it doubles for, under a
 * `__` prefix, or here. `lib/` would be wrong for the opposite reason: nothing in the shipped bundle
 * imports this.
 *
 * It returns rather than throws so that a failure reads as a diff — the rules that fired against the
 * empty list that should have — instead of as a stack trace with the interesting part in a message.
 *
 * What this can and cannot see is worth stating, because an automated pass invites the belief that
 * accessibility is now covered. In jsdom there is no layout and no canvas, so the rules about colour
 * contrast, focus order and anything that depends on where an element ends up cannot run here.
 * Contrast on the one screen where it is a correctness property is held to a number separately, in
 * `features/checkin/doorScreenLegibility.test.ts`, from the values themselves rather than from a
 * render. What axe does catch here is the structural half: names on controls, labels on inputs, valid
 * ARIA, heading order, landmarks and list structure.
 */
export async function accessibilityViolations(container: HTMLElement): Promise<string[]> {
  const results = await axe.run(container, {
    resultTypes: ["violations"],
    runOnly: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa", "best-practice"],
  });
  return results.violations.map(
    (violation) => `${violation.id}: ${violation.help} (${violation.nodes.length} nodes)`,
  );
}
