import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { useCurrentActor } from "../../../lib/auth";
import { describePhase } from "../describePhase";
import { useEventsBrowse } from "../hooks/useEventsBrowse";
import type { EventBrowseFilters, EventSort } from "../types";

const PAGE_SIZE = 20;

const SORT_OPTIONS: { value: EventSort; label: string }[] = [
  { value: "STARTS_AT_ASC", label: "Starting soonest" },
  { value: "STARTS_AT_DESC", label: "Starting latest" },
  { value: "CREATED_AT_DESC", label: "Newest" },
];

function isEventSort(value: string): value is EventSort {
  return SORT_OPTIONS.some((option) => option.value === value);
}

export function EventsBrowsePage() {
  const [searchInput, setSearchInput] = useState("");
  const [filters, setFilters] = useState<EventBrowseFilters>({ page: 0, size: PAGE_SIZE });

  const currentActor = useCurrentActor();
  const query = useEventsBrowse(filters);

  function handleSearchSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFilters((current) => ({ ...current, q: searchInput.trim() || undefined, page: 0 }));
  }

  function toggleOpenForRegistration() {
    setFilters((current) => ({
      ...current,
      openForRegistration: current.openForRegistration ? undefined : true,
      page: 0,
    }));
  }

  function toggleHasFreeSeat() {
    setFilters((current) => ({ ...current, hasFreeSeat: current.hasFreeSeat ? undefined : true, page: 0 }));
  }

  function handleSortChange(value: string) {
    setFilters((current) => ({ ...current, sort: isEventSort(value) ? value : undefined, page: 0 }));
  }

  function goToPage(page: number) {
    setFilters((current) => ({ ...current, page }));
  }

  const total = query.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">Events</h1>
        <Link to="/events/mine" className="text-sm text-slate-600 hover:underline">
          My events
        </Link>
      </div>

      <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-center gap-3">
        <input
          type="search"
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder="Search events"
          aria-label="Search events"
          className="rounded border px-3 py-2"
        />
        <button type="submit" className="rounded bg-slate-900 px-3 py-2 text-white">
          Search
        </button>
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={filters.openForRegistration === true}
            onChange={toggleOpenForRegistration}
          />
          Open for registration
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={filters.hasFreeSeat === true} onChange={toggleHasFreeSeat} />
          Has a free seat
        </label>
        <label className="flex items-center gap-2">
          Sort
          <select
            value={filters.sort ?? ""}
            onChange={(event) => handleSortChange(event.target.value)}
            className="rounded border px-2 py-1"
          >
            <option value="">Default</option>
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </form>

      {query.status === "pending" && <p role="status">Loading events…</p>}

      {query.status === "error" && <p role="alert">Could not load events ({query.error.code}).</p>}

      {query.status === "success" && (
        <>
          {query.data.items.length === 0 ? (
            <p>No events match your filters.</p>
          ) : (
            <ul className="flex flex-col gap-4">
              {query.data.items.map((item) => (
                <li key={item.id} className="rounded border p-4">
                  <Link to={`/events/${item.id}`} className="font-semibold hover:underline">
                    {item.title}
                  </Link>
                  <p className="text-sm text-slate-600">{item.description}</p>
                  <p className="text-sm">{describePhase(item)}</p>
                  {currentActor.data?.officerClubIds.includes(item.clubId) && (
                    <div className="flex flex-wrap gap-3">
                      <Link
                        to={`/officer/events/${item.id}/venue`}
                        className="text-sm font-medium text-slate-700 hover:underline"
                      >
                        Book a venue
                      </Link>
                      <Link
                        to={`/officer/events/${item.id}/capacity`}
                        className="text-sm font-medium text-slate-700 hover:underline"
                      >
                        Manage capacity
                      </Link>
                      <Link
                        to={`/officer/events/${item.id}/registration-form`}
                        className="text-sm font-medium text-slate-700 hover:underline"
                      >
                        Build registration form
                      </Link>
                      <Link
                        to={`/officer/events/${item.id}/registration-answers`}
                        className="text-sm font-medium text-slate-700 hover:underline"
                      >
                        View registration answers
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
              onClick={() => goToPage(filters.page - 1)}
              disabled={filters.page === 0}
              className="rounded border px-3 py-1 disabled:opacity-50"
            >
              Previous
            </button>
            <span>
              Page {filters.page + 1} of {pageCount}
            </span>
            <button
              type="button"
              onClick={() => goToPage(filters.page + 1)}
              disabled={filters.page + 1 >= pageCount}
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
