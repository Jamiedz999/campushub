/** The first retry is quick, because the common cause is a blip that has already passed. */
export const RECONNECT_FIRST_DELAY_MS = 1_000;

/**
 * The ceiling. A door screen is projected for a couple of hours and nobody is watching the console, so
 * the backoff stops growing well before the retries become rare enough to look like a dead screen.
 */
export const RECONNECT_MAX_DELAY_MS = 30_000;

/**
 * How long to wait before the nth attempt to reopen the socket.
 *
 * Exponential rather than fixed, because the failure this handles twice over is a server that is down:
 * every door screen in the building retries it, and a fixed one-second retry from all of them is a
 * self-inflicted load spike arriving exactly when the server is least able to take it.
 *
 * There is no cap on the number of attempts. The screen is correct while disconnected — it falls back
 * to re-reading on a timer — so there is nothing to be gained by eventually giving up on the socket.
 */
export function reconnectDelayMs(attempt: number): number {
  const exponential = RECONNECT_FIRST_DELAY_MS * 2 ** Math.max(0, attempt - 1);
  return Math.min(exponential, RECONNECT_MAX_DELAY_MS);
}
