import { afterEach, describe, expect, it } from "vitest";
import { accessibilityViolations } from "./accessibility";

/**
 * Every page test that asserts `accessibilityViolations(container)` is empty is only worth the run it
 * costs if a violation would actually come back. This is that proof: markup that is definitely wrong,
 * and the check reporting it.
 *
 * Without this the page tests would keep passing if the helper were quietly reduced to returning an
 * empty array — which is exactly the failure a green suite cannot show you.
 */
function render(html: string): HTMLElement {
  const container = document.createElement("div");
  container.innerHTML = html;
  document.body.append(container);
  return container;
}

afterEach(() => {
  document.body.replaceChildren();
});

describe("accessibilityViolations", () => {
  it("reports an image nobody can hear", async () => {
    const violations = await accessibilityViolations(render(`<img src="door.png">`));

    expect(violations).toHaveLength(1);
    expect(violations[0]).toContain("image-alt");
  });

  it("reports an input with no label, which is the whole registration form's failure mode", async () => {
    const violations = await accessibilityViolations(render(`<main><input type="text"></main>`));

    expect(violations.join(" ")).toContain("label");
  });

  it("names how many places each rule fired, so a fix can be told from a fix that missed one", async () => {
    const violations = await accessibilityViolations(render(`<img src="a.png"><img src="b.png">`));

    expect(violations[0]).toContain("(2 nodes)");
  });

  it("comes back empty on markup that is fine, which is what the page tests rely on", async () => {
    const violations = await accessibilityViolations(
      render(`<main><h1>Door</h1><img src="door.png" alt="The door code"></main>`),
    );

    expect(violations).toEqual([]);
  });
});
