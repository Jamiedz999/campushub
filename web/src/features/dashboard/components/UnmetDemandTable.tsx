import { Link } from "react-router";
import { unmetDemandRows } from "../series";
import type { EventTotals } from "../types";

/**
 * The Events people were still queued for when the doors closed — a table, because the question is
 * "which Event should have been bigger" and the answer is a list of names.
 *
 * Each row links to the Event's own page, which is the only drill-down Core has: the ADR settles that
 * there is no deeper view here.
 */
export function UnmetDemandTable({ events }: { events: EventTotals[] }) {
  const rows = unmetDemandRows(events);

  if (rows.length === 0) {
    return (
      <p className="text-sm text-slate-600">
        No Event in this range ended with anyone still on its Waitlist.
      </p>
    );
  }

  return (
    <table className="w-full text-left text-sm">
      <caption className="sr-only">
        Events that ended with Students still on the Waitlist, most over-subscribed first
      </caption>
      <thead>
        <tr className="border-b text-slate-600">
          <th scope="col" className="py-2">
            Event
          </th>
          <th scope="col" className="py-2">
            Club
          </th>
          <th scope="col" className="py-2 text-right">
            Capacity
          </th>
          <th scope="col" className="py-2 text-right">
            Still queued
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((event) => (
          <tr key={event.eventId} className="border-b last:border-b-0">
            <td className="py-2">
              <Link to={`/events/${event.eventId}`} className="underline">
                {event.title}
              </Link>
            </td>
            <td className="py-2">{event.clubName}</td>
            <td className="py-2 text-right">{event.capacity}</td>
            <td className="py-2 text-right font-semibold">{event.unmetDemand}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
