/**
 * The WCAG 2.2 contrast ratio between two sRGB colours, from the published formula.
 *
 * A pure function so that "the door screen is legible from the back of the room" can be a test rather
 * than an opinion — see `features/checkin/doorScreenLegibility.test.ts`, which reads the door screen's
 * own palette out of the stylesheet and holds every pair to AAA.
 */
export function contrastRatio(one: string, other: string): number {
  const lighter = Math.max(relativeLuminance(one), relativeLuminance(other));
  const darker = Math.min(relativeLuminance(one), relativeLuminance(other));
  return (lighter + 0.05) / (darker + 0.05);
}

function relativeLuminance(hex: string): number {
  const [red, green, blue] = channels(hex);
  return 0.2126 * toLinear(red) + 0.7152 * toLinear(green) + 0.0722 * toLinear(blue);
}

function channels(hex: string): [number, number, number] {
  const digits = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex.trim())?.[1];
  if (digits === undefined) {
    throw new Error(`${hex} is not a three- or six-digit hex colour`);
  }
  const full = digits.length === 3 ? [...digits].map((digit) => digit + digit).join("") : digits;
  const channel = (at: number) => Number.parseInt(full.slice(at, at + 2), 16) / 255;
  return [channel(0), channel(2), channel(4)];
}

// The sRGB transfer function: the eye's response to a channel is not its 0–1 value, which is why a
// mid-grey is nowhere near half as bright as white.
function toLinear(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
}
