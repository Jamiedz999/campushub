import { describe, expect, it } from "vitest";
import { scanUrl } from "./scanUrl";

describe("scanUrl", () => {
  it("points at this Event's scan page on the same origin", () => {
    expect(scanUrl("https://campushub.example", "event-1", "event-1.29566667.sig")).toBe(
      "https://campushub.example/checkin/event-1?token=event-1.29566667.sig",
    );
  });

  it("escapes a code so that no character of it can be read as part of the URL", () => {
    expect(scanUrl("https://campushub.example", "event-1", "a+b/c=d&e")).toBe(
      "https://campushub.example/checkin/event-1?token=a%2Bb%2Fc%3Dd%26e",
    );
  });
});
