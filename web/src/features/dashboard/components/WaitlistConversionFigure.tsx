import { formatCount, formatRate, waitlistAbandoned, waitlistConversion } from "../metrics";
import type { MetricTotals } from "../types";

/**
 * Waitlist conversion as one figure with its two components beside it — the ADR is explicit that this
 * is not a pie.
 *
 * The two components are printed because the denominator is the interesting half. Everyone who ever
 * queued includes the Students who gave up and left, and they are the reason this number is not
 * promoted over the queue's remaining length.
 */
export function WaitlistConversionFigure({ totals }: { totals: MetricTotals }) {
  const left = waitlistAbandoned(totals);

  return (
    <section aria-label="Waitlist conversion" className="rounded border p-4">
      <h3 className="text-sm font-medium text-slate-600">Waitlist conversion</h3>
      <p className="text-4xl font-bold leading-tight">{formatRate(waitlistConversion(totals))}</p>
      <p className="mt-1 text-sm text-slate-600">
        {formatCount(totals.promoted)} promoted of {formatCount(totals.everQueued)} who ever queued.
      </p>
      {left > 0 && (
        <p className="mt-1 text-sm text-slate-600">
          {formatCount(left)} left the Waitlist before the Event — counted in the denominator, because
          giving up is the strongest evidence the queue was too slow.
        </p>
      )}
    </section>
  );
}
