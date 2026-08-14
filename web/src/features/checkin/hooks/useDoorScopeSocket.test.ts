import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DoorSocketDouble } from "../doorSocketDouble";
import { useDoorScopeSocket } from "./useDoorScopeSocket";

function watchDoor(eventId = "event-1") {
  const onHint = vi.fn();
  const view = renderHook(() => useDoorScopeSocket(eventId, onHint));
  return { onHint, view };
}

describe("useDoorScopeSocket", () => {
  beforeEach(() => {
    DoorSocketDouble.reset();
    vi.stubGlobal("WebSocket", DoorSocketDouble);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("subscribes to one Event's door scope", () => {
    watchDoor();

    expect(DoorSocketDouble.current().url).toMatch(/\/ws\/events\/event-1\/attendance$/);
  });

  it("asks for a re-read the moment it connects", () => {
    // Before any message has arrived. Whatever happened while this screen was not listening is already
    // in the snapshot it is about to fetch.
    const { onHint } = watchDoor();

    act(() => DoorSocketDouble.current().connect());

    expect(onHint).toHaveBeenCalledTimes(1);
  });

  it("asks for a re-read on every hint, and for nothing else", () => {
    const { onHint } = watchDoor();
    act(() => DoorSocketDouble.current().connect());

    act(() => DoorSocketDouble.current().deliver('{"type":"attendance-changed","eventId":"event-1"}'));
    act(() => DoorSocketDouble.current().deliver('{"type":"attendance-changed","eventId":"event-1"}'));
    act(() => DoorSocketDouble.current().deliver("who knows"));

    expect(onHint).toHaveBeenCalledTimes(3); // one for the connect, two for the hints
  });

  it("reports whether the socket is up, so the caller can fall back to a timer", () => {
    const { view } = watchDoor();
    expect(view.result.current).toBe(false);

    act(() => DoorSocketDouble.current().connect());
    expect(view.result.current).toBe(true);

    act(() => DoorSocketDouble.current().drop());
    expect(view.result.current).toBe(false);
  });

  it("reopens a dropped socket after backing off, and re-reads when it is back", () => {
    vi.useFakeTimers();
    const { onHint } = watchDoor();
    act(() => DoorSocketDouble.current().connect());
    act(() => DoorSocketDouble.current().drop());

    // Nothing is retried instantly: a screen that reconnects in a tight loop is a screen that hammers
    // a server that has just gone down.
    expect(DoorSocketDouble.opened).toHaveLength(1);

    act(() => vi.advanceTimersByTime(1_000));
    expect(DoorSocketDouble.opened).toHaveLength(2);

    act(() => DoorSocketDouble.current().connect());
    expect(onHint).toHaveBeenCalledTimes(2);
  });

  it("backs off further with each failed attempt", () => {
    vi.useFakeTimers();
    watchDoor();

    act(() => DoorSocketDouble.current().drop());
    act(() => vi.advanceTimersByTime(1_000));
    act(() => DoorSocketDouble.current().drop());

    act(() => vi.advanceTimersByTime(1_000));
    expect(DoorSocketDouble.opened).toHaveLength(2);

    act(() => vi.advanceTimersByTime(1_000));
    expect(DoorSocketDouble.opened).toHaveLength(3);
  });

  it("gives up the socket when the screen goes away", () => {
    vi.useFakeTimers();
    const { view } = watchDoor();
    act(() => DoorSocketDouble.current().connect());

    view.unmount();

    expect(DoorSocketDouble.current().closedByTheScreen).toBe(true);
    // The close is not mistaken for an outage: an unmounted screen that kept reconnecting would be a
    // socket per navigation, all of them still holding a scope on the server.
    act(() => vi.advanceTimersByTime(60_000));
    expect(DoorSocketDouble.opened).toHaveLength(1);
  });

  it("opens nothing until it knows which door it is watching", () => {
    watchDoor("");

    expect(DoorSocketDouble.opened).toHaveLength(0);
  });
});
