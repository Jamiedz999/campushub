import { QRCodeSVG } from "qrcode.react";
import { Link, useParams } from "react-router";
import { formatCampusTime } from "../../../lib/campusTimeZone";
import { useAttendanceRoster } from "../hooks/useAttendanceRoster";
import { useDoorCode } from "../hooks/useDoorCode";
import { useMarkPresent } from "../hooks/useMarkPresent";
import { scanUrl } from "../scanUrl";
import type { Attendee } from "../types";

/**
 * The screen the Club Officer projects at the door, and the manual override beside it.
 *
 * The QR code carries a link to this Event's scan page with the rotating code in it, so a Student
 * uses their phone's own camera and lands already signed in — the session is the half of the proof
 * the code cannot give. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 */
export function OfficerDoorPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const doorCode = useDoorCode(eventId ?? "");
  const roster = useAttendanceRoster(eventId ?? "");
  const override = useMarkPresent(eventId ?? "");

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  if (doorCode.status === "pending") {
    return <p role="status">Loading the door screen…</p>;
  }

  if (doorCode.status === "error") {
    return <p role="alert">Could not open the door screen ({doorCode.error.code}).</p>;
  }

  const code = doorCode.data;

  return (
    <main className="mx-auto flex max-w-4xl flex-col gap-6 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      <h1 className="text-xl font-semibold">Door · {code.title}</h1>

      <section className="flex flex-wrap items-center gap-8 rounded border p-6" aria-label="Door code">
        {code.checkInOpen ? (
          <QRCodeSVG
            value={scanUrl(window.location.origin, code.eventId, code.token)}
            size={220}
            marginSize={2}
          />
        ) : (
          <p className="max-w-xs rounded border border-dashed p-6 text-slate-600">
            Check-in opens at {formatCampusTime(code.checkInOpensAt)} and closes at{" "}
            {formatCampusTime(code.checkInClosesAt)}. The code below will not admit anyone until then.
          </p>
        )}
        <div className="flex flex-col gap-2">
          <p className="text-5xl font-bold leading-none">
            {code.attendedCount}
            <span className="text-2xl font-normal text-slate-500"> / {code.enrolledCount}</span>
          </p>
          <p className="text-sm text-slate-500">checked in</p>
          <p className="text-sm text-slate-500">The code changes every minute. Students scan it themselves.</p>
        </div>
      </section>

      <section className="flex flex-col gap-3" aria-label="Manual override">
        <h2 className="text-lg font-semibold">Mark present</h2>
        <p className="text-sm text-slate-600">
          For a phone that failed or a scan that will not read. A manual record is always distinguishable
          from a scanned one.
        </p>

        {roster.status === "pending" && <p role="status">Loading the roster…</p>}
        {roster.status === "error" && <p role="alert">Could not load the roster ({roster.error.code}).</p>}
        {roster.status === "success" && (
          <ul aria-label="Enrolled students" className="flex flex-col gap-2">
            {roster.data.items.length === 0 ? (
              <li className="rounded border border-dashed p-3 text-slate-600">Nobody holds a Seat yet.</li>
            ) : (
              roster.data.items.map((attendee) => (
                <li key={attendee.studentId} className="flex items-center justify-between gap-3 rounded border p-3">
                  <span>{attendee.displayName}</span>
                  <span className="flex items-center gap-3">
                    <span className="text-sm text-slate-600">{describeAttendance(attendee)}</span>
                    {attendee.method === null && (
                      <button
                        type="button"
                        onClick={() => override.mutate(attendee.studentId)}
                        disabled={override.isPending}
                        className="rounded border px-3 py-1 disabled:opacity-50"
                      >
                        Mark present
                      </button>
                    )}
                  </span>
                </li>
              ))
            )}
          </ul>
        )}

        {override.isError && (
          <p role="alert">That Student could not be marked present ({override.error.code}).</p>
        )}
        {override.isSuccess && (
          <p role="status" aria-label="override result">
            Marked present.
          </p>
        )}
      </section>
    </main>
  );
}

function describeAttendance(attendee: Attendee): string {
  if (attendee.method === null || attendee.attendedAt === null) {
    return "Not in";
  }
  const at = formatCampusTime(attendee.attendedAt);
  return attendee.method === "SCANNED" ? `Scanned ${at}` : `Manual ${at}`;
}
