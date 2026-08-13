import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router";
import { useOfficerEvent } from "../hooks/useOfficerEvent";
import { useRaiseEventCapacity } from "../hooks/useRaiseEventCapacity";
import type { EventOfficerView } from "../types";

interface CapacityFormProps {
  event: EventOfficerView;
  raiseCapacity: ReturnType<typeof useRaiseEventCapacity>;
}

function CapacityForm({ event, raiseCapacity }: CapacityFormProps) {
  const [capacity, setCapacity] = useState(event.capacity);
  const increase = Math.max(0, capacity - event.capacity);
  const newFreePlaces = Math.max(0, capacity - event.enrolledCount);
  const admittedImmediately = Math.min(event.waitlistCount, newFreePlaces);

  function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    raiseCapacity.mutate(capacity);
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <p>
        Current capacity: {event.capacity}. Waiting Students: {event.waitlistCount}.
      </p>
      <label className="flex flex-col gap-1">
        New capacity
        <input
          type="number"
          min={event.capacity + 1}
          value={capacity}
          onChange={(changeEvent) => setCapacity(Number(changeEvent.target.value))}
          className="w-40 rounded border px-3 py-2"
        />
      </label>

      {increase > 0 && (
        <p className="rounded border border-amber-300 bg-amber-50 p-3 text-amber-900">
          This will admit {admittedImmediately} waiting Students immediately.
        </p>
      )}

      <button
        type="submit"
        disabled={increase === 0 || raiseCapacity.isPending}
        className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
      >
        {raiseCapacity.isPending ? "Raising…" : "Raise capacity"}
      </button>

      {raiseCapacity.isError && <p role="alert">Capacity could not be raised ({raiseCapacity.error.code}).</p>}
    </form>
  );
}

export function OfficerCapacityPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const query = useOfficerEvent(eventId ?? "");
  const raiseCapacity = useRaiseEventCapacity(eventId ?? "");

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-5 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      {query.status === "pending" && <p role="status">Loading Event capacity…</p>}
      {query.status === "error" && <p role="alert">Could not load Event ({query.error.code}).</p>}
      {query.status === "success" && (
        <>
          <h1 className="text-xl font-semibold">Raise capacity · {query.data.title}</h1>
          <CapacityForm event={query.data} raiseCapacity={raiseCapacity} />
        </>
      )}
    </main>
  );
}
