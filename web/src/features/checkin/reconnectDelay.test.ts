import { describe, expect, it } from "vitest";
import { RECONNECT_MAX_DELAY_MS, reconnectDelayMs } from "./reconnectDelay";

describe("reconnectDelayMs", () => {
  it("retries a blip almost immediately and then backs off", () => {
    expect(reconnectDelayMs(1)).toBe(1_000);
    expect(reconnectDelayMs(2)).toBe(2_000);
    expect(reconnectDelayMs(3)).toBe(4_000);
    expect(reconnectDelayMs(4)).toBe(8_000);
  });

  it("stops growing, so a long outage never looks like a dead screen", () => {
    expect(reconnectDelayMs(10)).toBe(RECONNECT_MAX_DELAY_MS);
    expect(reconnectDelayMs(1_000)).toBe(RECONNECT_MAX_DELAY_MS);
  });

  it("treats a first attempt as the first attempt however it is counted", () => {
    expect(reconnectDelayMs(0)).toBe(1_000);
  });
});
