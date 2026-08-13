import { Link, useParams } from "react-router";
import { describePhase } from "../describePhase";
import { describeRegistrationError } from "../describeRegistrationError";
import { useEventRegistration } from "../hooks/useEventRegistration";
import { useRegisterForEvent } from "../hooks/useRegisterForEvent";
import { useWithdrawFromEvent } from "../hooks/useWithdrawFromEvent";
import type { Phase } from "../../../types/phase";

function canWithdraw(phase: Phase) {
  return ["SCHEDULED", "REGISTRATION_OPEN", "FULL", "REGISTRATION_CLOSED"].includes(phase);
}

export function EventRegistrationPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const query = useEventRegistration(eventId ?? "");
  const registrationMutation = useRegisterForEvent(eventId ?? "");
  const withdrawalMutation = useWithdrawFromEvent(eventId ?? "");

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>

      {query.status === "pending" && <p role="status">Loading event…</p>}

      {query.status === "error" && <p role="alert">{describeRegistrationError(query.error.code)}</p>}

      {query.status === "success" && (
        <>
          <h1 className="text-xl font-semibold">{query.data.title}</h1>
          <p className="text-sm text-slate-600">{query.data.description}</p>
          <p className="text-sm">{describePhase(query.data)}</p>

          {query.data.enrolled ? (
            <p className="font-medium text-emerald-700">
              {query.data.enrollmentVia === "PROMOTED"
                ? "You were on the Waitlist — you’re in."
                : "You’re registered for this Event."}
            </p>
          ) : query.data.waitlistPosition !== null ? (
            <p className="font-medium text-indigo-700">
              You&rsquo;re number {query.data.waitlistPosition} on the Waitlist.
            </p>
          ) : (
            (query.data.phase === "REGISTRATION_OPEN" || query.data.phase === "FULL") && (
              <button
                type="button"
                onClick={() => registrationMutation.mutate()}
                disabled={registrationMutation.isPending}
                className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
              >
                {registrationMutation.isPending
                  ? "Registering…"
                  : query.data.phase === "FULL"
                    ? "Join the Waitlist"
                    : "Register"}
              </button>
            )
          )}

          {(query.data.enrolled || query.data.waitlistPosition !== null) && canWithdraw(query.data.phase) && (
            <button
              type="button"
              onClick={() => withdrawalMutation.mutate()}
              disabled={withdrawalMutation.isPending}
              className="w-fit rounded border px-4 py-2 disabled:opacity-50"
            >
              {query.data.enrolled
                ? withdrawalMutation.isPending
                  ? "Withdrawing…"
                  : "Withdraw from Event"
                : withdrawalMutation.isPending
                  ? "Leaving…"
                  : "Leave the Waitlist"}
            </button>
          )}

          {registrationMutation.status === "error" && (
            <p role="alert">{describeRegistrationError(registrationMutation.error.code)}</p>
          )}

          {withdrawalMutation.status === "error" && (
            <p role="alert">{describeRegistrationError(withdrawalMutation.error.code)}</p>
          )}
        </>
      )}
    </main>
  );
}
