import { describe, expect, it } from "vitest";
import { doorScopeUrl } from "./doorScopeUrl";

describe("doorScopeUrl", () => {
  it("watches one Event's door on the origin the page came from", () => {
    expect(doorScopeUrl("http://localhost:5173", "event-1")).toBe(
      "ws://localhost:5173/ws/events/event-1/attendance",
    );
  });

  it("stays encrypted wherever the page is", () => {
    // A ws:// socket opened from an https:// page is refused by the browser outright, so getting this
    // wrong would not degrade the screen — it would leave it permanently on the fallback timer.
    expect(doorScopeUrl("https://campushub.example", "event-1")).toBe(
      "wss://campushub.example/ws/events/event-1/attendance",
    );
  });

  it("escapes the Event id rather than letting it shape the path", () => {
    expect(doorScopeUrl("https://campushub.example", "../../evil")).toBe(
      "wss://campushub.example/ws/events/..%2F..%2Fevil/attendance",
    );
  });
});
