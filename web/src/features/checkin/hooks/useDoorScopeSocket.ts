import { useEffect, useRef, useState } from "react";
import { isAttendanceHint } from "../attendanceHint";
import { doorScopeUrl } from "../doorScopeUrl";
import { reconnectDelayMs } from "../reconnectDelay";

/**
 * Subscribes to one Event's door scope and calls {@code onHint} whenever the screen should re-read.
 *
 * <p><b>It calls back on every connect, not only on every message.</b> That single line is what makes
 * a dropped connection harmless: a screen that was offline for a minute re-reads the moment it is back
 * and lands on the same answer as one that never dropped, without the server having to remember what
 * it missed. Messages are then just an optimisation on top of that guarantee — they make the re-read
 * prompt instead of eventual.
 *
 * <p>Returns whether the socket is currently up, so a caller can fall back to a timer while it is not.
 */
export function useDoorScopeSocket(eventId: string, onHint: () => void): boolean {
  const [connected, setConnected] = useState(false);

  // Held in a ref so a caller may pass an inline callback without every render tearing the socket down
  // and building a new one — which would reconnect, re-read, re-render, and do it all again.
  const hint = useRef(onHint);
  useEffect(() => {
    hint.current = onHint;
  });

  useEffect(() => {
    if (eventId === "") {
      return;
    }

    let socket: WebSocket | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let abandoned = false;

    const open = () => {
      socket = new WebSocket(doorScopeUrl(window.location.origin, eventId));

      socket.onopen = () => {
        attempt = 0;
        setConnected(true);
        hint.current();
      };

      socket.onmessage = (message: MessageEvent) => {
        if (isAttendanceHint(message.data)) {
          hint.current();
        }
      };

      // Also the path a refused handshake takes. An Officer whose grant was revoked mid-Event keeps
      // retrying on the same backoff and sees nothing here — their re-reads are refused too, and the
      // roster below says so with the code the server sent.
      socket.onclose = () => {
        setConnected(false);
        if (abandoned) {
          return;
        }
        attempt += 1;
        retry = setTimeout(open, reconnectDelayMs(attempt));
      };
    };

    open();

    return () => {
      abandoned = true;
      clearTimeout(retry);
      socket?.close();
    };
  }, [eventId]);

  return connected;
}
