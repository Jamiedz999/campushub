/**
 * A stand-in for the browser's WebSocket, so the door screen's behaviour under a lossy connection can
 * be driven directly: open it, drop it, deliver a frame, deliver rubbish.
 *
 * jsdom has no server to talk to, and the properties worth proving here are all about what the screen
 * does *between* connections — which a real socket would only ever let a test wait for.
 */
export class DoorSocketDouble {
  static opened: DoorSocketDouble[] = [];

  static reset(): void {
    DoorSocketDouble.opened = [];
  }

  /** The socket the screen is currently using — the last one it opened. */
  static current(): DoorSocketDouble {
    const socket = DoorSocketDouble.opened.at(-1);
    if (socket === undefined) {
      throw new Error("The screen has not opened a socket");
    }
    return socket;
  }

  readonly url: string;
  onopen: (() => void) | null = null;
  onmessage: ((message: { data: unknown }) => void) | null = null;
  onclose: (() => void) | null = null;
  closedByTheScreen = false;

  constructor(url: string) {
    this.url = url;
    DoorSocketDouble.opened.push(this);
  }

  /** The handshake succeeded. */
  connect(): void {
    this.onopen?.();
  }

  /** A frame arrived. */
  deliver(frame: unknown): void {
    this.onmessage?.({ data: frame });
  }

  /** The connection died on its own — the wifi went, or the server restarted. */
  drop(): void {
    this.onclose?.();
  }

  /** What the browser calls when the screen navigates away. */
  close(): void {
    this.closedByTheScreen = true;
    this.onclose?.();
  }
}
