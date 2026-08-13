import { useState } from "react";
import { Link } from "react-router";
import { describePhase } from "../describePhase";
import { useMyEvents } from "../hooks/useMyEvents";

const PAGE_SIZE = 20;

export function MyEventsPage() {
  const [page, setPage] = useState(0);
  const query = useMyEvents({ page, size: PAGE_SIZE });

  const total = query.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      <h1 className="text-xl font-semibold">My events</h1>

      {query.status === "pending" && <p role="status">Loading your events…</p>}

      {query.status === "error" && <p role="alert">Could not load your events ({query.error.code}).</p>}

      {query.status === "success" && (
        <>
          {query.data.items.length === 0 ? (
            <p>You haven&rsquo;t registered for any events yet.</p>
          ) : (
            <ul className="flex flex-col gap-4">
              {query.data.items.map((item) => (
                <li key={item.id} className="rounded border p-4">
                  <Link to={`/events/${item.id}`} className="font-semibold hover:underline">
                    {item.title}
                  </Link>
                  <p className="text-sm text-slate-600">{item.description}</p>
                  <p className="text-sm">{describePhase(item)}</p>
                  {item.enrollmentVia === "PROMOTED" && (
                    <p className="font-medium text-emerald-700">You were on the Waitlist — you&rsquo;re in.</p>
                  )}
                  {item.answersSaved === false && (
                    <div className="mt-2 flex items-center gap-3 text-sm text-amber-800">
                      <span>Answers still need saving.</span>
                      <Link to={`/events/${item.id}`} className="font-medium underline">
                        Retry answers
                      </Link>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}

          <nav className="flex items-center gap-3" aria-label="Pagination">
            <button
              type="button"
              onClick={() => setPage((current) => current - 1)}
              disabled={page === 0}
              className="rounded border px-3 py-1 disabled:opacity-50"
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {pageCount}
            </span>
            <button
              type="button"
              onClick={() => setPage((current) => current + 1)}
              disabled={page + 1 >= pageCount}
              className="rounded border px-3 py-1 disabled:opacity-50"
            >
              Next
            </button>
          </nav>
        </>
      )}
    </main>
  );
}
