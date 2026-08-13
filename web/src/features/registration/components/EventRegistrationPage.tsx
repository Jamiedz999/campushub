import { Link, useParams } from "react-router";
import { describePhase } from "../describePhase";
import { describeRegistrationError } from "../describeRegistrationError";
import { useEventRegistration } from "../hooks/useEventRegistration";
import { useRegisterForEvent } from "../hooks/useRegisterForEvent";

export function EventRegistrationPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const query = useEventRegistration(eventId ?? "");
  const mutation = useRegisterForEvent(eventId ?? "");

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
            <p className="font-medium text-emerald-700">You&rsquo;re registered for this Event.</p>
          ) : (
            query.data.phase === "REGISTRATION_OPEN" && (
              <button
                type="button"
                onClick={() => mutation.mutate()}
                disabled={mutation.isPending}
                className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
              >
                {mutation.isPending ? "Registering…" : "Register"}
              </button>
            )
          )}

          {mutation.status === "error" && (
            <p role="alert">{describeRegistrationError(mutation.error.code)}</p>
          )}
        </>
      )}
    </main>
  );
}
