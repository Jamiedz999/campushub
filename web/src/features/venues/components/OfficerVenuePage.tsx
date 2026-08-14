import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import type { ApiError } from "../../../lib/apiError";
import {
  bookVenueSlot,
  getVenueBookingEvent,
  getVenueDay,
  listVenues,
  releaseVenueSlot,
} from "../api/venueBooking";
import type { VenueBookingEvent, VenueDay, VenuePage } from "../types";
import { formatCampusTime } from "../../../lib/campusTimeZone";
import { campusDate, formatMinute } from "../venueTime";

interface BookingChoice {
  eventId: string;
  venueId: string;
  startsAt: string;
  endsAt: string;
  date: string;
}

export function OfficerVenuePage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [chosenVenueId, setChosenVenueId] = useState("");
  const queryClient = useQueryClient();

  const eventQuery = useQuery<VenueBookingEvent, ApiError>({
    queryKey: ["venues", "event", eventId],
    queryFn: () => getVenueBookingEvent(eventId ?? ""),
    enabled: eventId !== undefined,
  });
  const venuesQuery = useQuery<VenuePage, ApiError>({
    queryKey: ["venues", "list"],
    queryFn: listVenues,
    enabled: eventId !== undefined,
  });

  const currentVenueId = eventQuery.data?.venueId ?? "";
  const currentVenueExists = venuesQuery.data?.items.some((venue) => venue.id === currentVenueId) ?? false;
  const selectedVenueId =
    chosenVenueId || (currentVenueExists ? currentVenueId : (venuesQuery.data?.items[0]?.id ?? ""));
  const date = eventQuery.data === undefined ? "" : campusDate(eventQuery.data.startsAt);

  const dayQuery = useQuery<VenueDay, ApiError>({
    queryKey: ["venues", "day", selectedVenueId, date],
    queryFn: () => getVenueDay(selectedVenueId, date),
    enabled: selectedVenueId !== "" && date !== "",
  });

  const booking = useMutation<void, ApiError, BookingChoice>({
    mutationFn: (choice) =>
      bookVenueSlot(choice.eventId, choice.venueId, choice.startsAt, choice.endsAt),
    onSuccess: async (_response, choice) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["venues", "event", choice.eventId] }),
        queryClient.invalidateQueries({
          queryKey: ["venues", "day", choice.venueId, choice.date],
        }),
      ]);
    },
  });

  const release = useMutation<void, ApiError>({
    mutationFn: () => releaseVenueSlot(eventId ?? ""),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["venues", "event", eventId] }),
        queryClient.invalidateQueries({ queryKey: ["venues", "day"] }),
      ]);
    },
  });

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  if (eventQuery.status === "pending" || venuesQuery.status === "pending") {
    return <p role="status">Loading Venue booking…</p>;
  }

  if (eventQuery.status === "error" || venuesQuery.status === "error") {
    const code = eventQuery.error?.code ?? venuesQuery.error?.code ?? "NETWORK_ERROR";
    return <p role="alert">Could not load Venue booking ({code}).</p>;
  }

  const event = eventQuery.data;
  const venues = venuesQuery.data.items;
  const bookingRefusal = booking.isError
    ? booking.error.code === "SLOT_TAKEN"
      ? "That slot was taken moments ago. Your Event has not changed. Choose another Venue."
      : `The Venue could not be booked (${booking.error.code}). Your Event has not changed.`
    : null;

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-5 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      <h1 className="text-xl font-semibold">Book a venue · {event.title}</h1>
      <p>
        Event time: {formatCampusTime(event.startsAt)}–{formatCampusTime(event.endsAt)} on {date}
      </p>

      {venues.length === 0 ? (
        <p>No Venues are available yet.</p>
      ) : (
        <>
          <label className="flex w-fit flex-col gap-1">
            Venue
            <select
              value={selectedVenueId}
              onChange={(changeEvent) => {
                booking.reset();
                release.reset();
                setChosenVenueId(changeEvent.target.value);
              }}
              className="rounded border px-3 py-2"
            >
              {venues.map((venue) => (
                <option key={venue.id} value={venue.id}>
                  {venue.name}
                </option>
              ))}
            </select>
          </label>

          {dayQuery.status === "pending" && <p role="status">Loading Venue timeline…</p>}
          {dayQuery.status === "error" && (
            <p role="alert">Could not load Venue timeline ({dayQuery.error.code}).</p>
          )}
          {dayQuery.status === "success" && (
            <ol aria-label={`${dayQuery.data.venue.name} timeline`} className="flex flex-col gap-2">
              {dayQuery.data.bookings.length === 0 ? (
                <li className="rounded border border-dashed p-3 text-slate-600">No bookings yet.</li>
              ) : (
                dayQuery.data.bookings.map((heldSlot) => (
                  <li key={`${heldSlot.eventId}-${heldSlot.startMinute}`} className="rounded border p-3">
                    <span className="font-medium">
                      {formatMinute(heldSlot.startMinute)}–{formatMinute(heldSlot.endMinute)}
                    </span>{" "}
                    · Event {heldSlot.eventId}
                  </li>
                ))
              )}
            </ol>
          )}

          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() =>
                booking.mutate({
                  eventId: event.id,
                  venueId: selectedVenueId,
                  startsAt: event.startsAt,
                  endsAt: event.endsAt,
                  date,
                })
              }
              disabled={booking.isPending || dayQuery.status !== "success"}
              className="rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
            >
              {booking.isPending ? "Booking…" : "Book this venue"}
            </button>
            {event.venueId !== null && (
              <button
                type="button"
                onClick={() => release.mutate()}
                disabled={release.isPending}
                className="rounded border px-4 py-2 disabled:opacity-50"
              >
                {release.isPending ? "Releasing…" : "Release venue"}
              </button>
            )}
          </div>

          {bookingRefusal !== null && <p role="alert">{bookingRefusal}</p>}
          {release.isError && <p role="alert">Venue could not be released ({release.error.code}).</p>}
          {booking.isSuccess && (
            <p role="status" aria-label="booking result">
              Venue booked.
            </p>
          )}
          {release.isSuccess && (
            <p role="status" aria-label="booking result">
              Venue released.
            </p>
          )}
        </>
      )}
    </main>
  );
}
