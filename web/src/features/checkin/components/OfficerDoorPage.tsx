import { QRCodeSVG } from "qrcode.react";
import type { ReactNode } from "react";
import { Link, useParams } from "react-router";
import { formatCampusTime } from "../../../lib/campusTimeZone";
import { useAttendanceRoster } from "../hooks/useAttendanceRoster";
import { useDoorCode } from "../hooks/useDoorCode";
import { useMarkPresent } from "../hooks/useMarkPresent";
import { rosterProgress } from "../rosterProgress";
import { scanUrl } from "../scanUrl";
import type { RosterEntry } from "../types";

/**
 * The screen the Club Officer projects at the door, and the manual override beside it.
 *
 * The QR code carries a link to this Event's scan page with the rotating code in it, so a Student
 * uses their phone's own camera and lands already signed in — the session is the half of the proof
 * the code cannot give. See docs/adr/07-define-qr-checkin-and-anti-fraud.md.
 *
 * Everything here is sized and coloured from the `door-` tokens in index.css rather than from
 * Tailwind's ordinary palette, because this screen is thrown onto a wall and read from the back of a
 * room with the lights up. Those tokens are held to WCAG AAA and to a minimum size by
 * `doorScreenLegibility.test.ts`; the reason they are tokens at all is so that one test can hold
 * them.
 */
export function OfficerDoorPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const doorCode = useDoorCode(eventId ?? "");
  const { roster, live } = useAttendanceRoster(eventId ?? "");
  const override = useMarkPresent(eventId ?? "");

  // Each of these is a whole screen, so each is a landmark with a heading rather than a bare
  // paragraph: the door screen is often the first thing on a projector, and "there is nothing here"
  // has to be readable from the same distance as everything else.
  if (eventId === undefined) {
    return <DoorMessage role="alert">No Event was specified.</DoorMessage>;
  }

  if (doorCode.status === "pending") {
    return <DoorMessage role="status">Loading the door screen…</DoorMessage>;
  }

  if (doorCode.status === "error") {
    return <DoorMessage role="alert">Could not open the door screen ({doorCode.error.code}).</DoorMessage>;
  }

  const code = doorCode.data;
  const progress = rosterProgress(roster.data?.items ?? []);

  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-8 bg-door-surface p-8 text-door-body text-door-ink">
      <Link to="/events" className="text-door-caption text-door-muted underline">
        &larr; Back to events
      </Link>
      <h1 className="text-door-title font-semibold">Door · {code.title}</h1>

      <section
        className="flex flex-wrap items-center gap-10 rounded border border-door-line p-8"
        aria-label="Door code"
      >
        {code.checkInOpen ? (
          <QRCodeSVG
            value={scanUrl(window.location.origin, code.eventId, code.token)}
            size={320}
            marginSize={2}
            title={`Check-in code for ${code.title}. Point a phone camera at it.`}
          />
        ) : (
          <p className="max-w-md rounded border border-dashed border-door-line p-8 text-door-muted">
            Check-in opens at {formatCampusTime(code.checkInOpensAt)} and closes at{" "}
            {formatCampusTime(code.checkInClosesAt)}. The code below will not admit anyone until then.
          </p>
        )}
        <div className="flex flex-col gap-3">
          <p className="text-door-count leading-none font-bold">
            {progress.attended}
            <span className="text-door-title font-normal text-door-muted"> / {progress.enrolled}</span>
          </p>
          <p className="text-door-body text-door-muted">
            checked in{progress.manual > 0 && ` · ${progress.manual} marked by hand`}
          </p>
          {/* Said out loud rather than left to be inferred from a number that has stopped moving: a
              screen that is a few seconds behind looks exactly like a quiet door. Which of the two it
              is, is carried by the sentence — the colour only ever agrees with it, because a red and
              a green both dark enough to read off a wall are nearly the same colour to anyone who
              cannot separate the two. */}
          <p
            className={`text-door-caption ${live ? "text-door-good" : "text-door-warn"}`}
            role="status"
            aria-label="Live count"
          >
            {live
              ? "Counting live as people scan."
              : "Not connected — counting by re-reading every few seconds."}
          </p>
          <p className="text-door-caption text-door-muted">
            Students scan the code themselves. It rotates at {formatCampusTime(code.rotatesAt)} and the
            screen refreshes on its own — a code that has just rotated still works for a minute.
          </p>
        </div>
      </section>

      <section className="flex flex-col gap-4" aria-label="Manual override">
        <h2 className="text-door-title font-semibold">Mark present</h2>
        <p className="text-door-caption text-door-muted">
          For a phone that failed or a scan that will not read. A manual record is always distinguishable
          from a scanned one.
        </p>

        {roster.status === "pending" && <p role="status">Loading the roster…</p>}
        {roster.status === "error" && <p role="alert">Could not load the roster ({roster.error.code}).</p>}
        {roster.status === "success" && (
          <ul aria-label="Enrolled students" className="flex flex-col gap-3">
            {roster.data.items.length === 0 ? (
              <li className="rounded border border-dashed border-door-line p-4 text-door-muted">
                Nobody holds a Seat yet.
              </li>
            ) : (
              roster.data.items.map((entry) => (
                <li
                  key={entry.studentId}
                  className="flex items-center justify-between gap-4 rounded border border-door-line p-4"
                >
                  <span>{entry.displayName}</span>
                  <span className="flex items-center gap-4">
                    <span className="text-door-caption text-door-muted">{describeAttendance(entry)}</span>
                    {entry.method === null && (
                      <button
                        type="button"
                        onClick={() => override.mutate(entry.studentId)}
                        disabled={override.isPending}
                        className="rounded border border-door-line px-4 py-2 disabled:opacity-50"
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

interface DoorMessageProps {
  role: "alert" | "status";
  children: ReactNode;
}

function DoorMessage({ role, children }: DoorMessageProps) {
  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-6 bg-door-surface p-8 text-door-ink">
      <h1 className="text-door-title font-semibold">Door</h1>
      <p role={role} className="text-door-body">
        {children}
      </p>
    </main>
  );
}

function describeAttendance(entry: RosterEntry): string {
  if (entry.method === null || entry.at === null) {
    return "Not in";
  }
  const at = formatCampusTime(entry.at);
  return entry.method === "SCANNED" ? `Scanned ${at}` : `Manual ${at}`;
}
