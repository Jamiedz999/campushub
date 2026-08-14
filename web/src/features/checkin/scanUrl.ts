/**
 * What the door's QR code actually carries: an absolute link to this Event's scan page with the code
 * of the moment in it.
 *
 * The Student uses their phone's own camera rather than a scanner inside the app, and lands on a page
 * where they are already signed in — which is what lets the code prove presence while the session
 * proves identity. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 */
export function scanUrl(origin: string, eventId: string, token: string): string {
  return `${origin}/checkin/${eventId}?token=${encodeURIComponent(token)}`;
}
