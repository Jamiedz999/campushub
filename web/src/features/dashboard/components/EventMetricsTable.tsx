import { Link } from "react-router";
import { attendanceRate, fillRate, formatRate, noShowRate } from "../metrics";
import type { EventTotals } from "../types";

/**
 * Each Event's own metrics — the half of a Club Officer's view the charts do not give them. The
 * grouped bar shows how the Events compare; this says how each one actually did, against the same
 * denominators the headline figures use, so a row and the total above it can never mean different
 * things by "attendance rate".
 *
 * The Event's title links to its own page, which is the only drill-down Core has.
 */
export function EventMetricsTable({ events }: { events: EventTotals[] }) {
  if (events.length === 0) {
    return <p className="text-sm text-slate-600">No Event finished in this range.</p>;
  }

  return (
    <table className="w-full text-left text-sm">
      <caption className="sr-only">
        Every finished Event in this range with its fill, attendance and no-show rates
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
            Enrolled
          </th>
          <th scope="col" className="py-2 text-right">
            Attended
          </th>
          <th scope="col" className="py-2 text-right">
            Fill
          </th>
          <th scope="col" className="py-2 text-right">
            Attendance
          </th>
          <th scope="col" className="py-2 text-right">
            No-show
          </th>
        </tr>
      </thead>
      <tbody>
        {events.map((event) => (
          <tr key={event.eventId} className="border-b last:border-b-0">
            <td className="py-2">
              <Link to={`/events/${event.eventId}`} className="underline">
                {event.title}
              </Link>
            </td>
            <td className="py-2">{event.clubName}</td>
            <td className="py-2 text-right">
              {event.enrolled} / {event.capacity}
            </td>
            <td className="py-2 text-right">{event.attended}</td>
            <td className="py-2 text-right">{formatRate(fillRate(event))}</td>
            <td className="py-2 text-right">{formatRate(attendanceRate(event))}</td>
            <td className="py-2 text-right">{formatRate(noShowRate(event))}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
