/**
 * The socket the door screen watches: one Event's scope, on the same origin the page came from.
 *
 * The scope is the URL rather than a message sent after connecting, because the server authorizes it
 * at the handshake and never again — see the realtime module. A client that could name its own scope
 * afterwards would be naming it after the only check.
 */
export function doorScopeUrl(origin: string, eventId: string): string {
  return `${origin.replace(/^http/, "ws")}/ws/events/${encodeURIComponent(eventId)}/attendance`;
}
