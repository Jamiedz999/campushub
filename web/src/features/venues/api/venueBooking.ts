import { httpClient } from "../../../lib/httpClient";
import type { VenueBookingEvent, VenueDay, VenuePage } from "../types";

export async function getVenueBookingEvent(eventId: string): Promise<VenueBookingEvent> {
  const response = await httpClient.get<VenueBookingEvent>(`/events/${eventId}`);
  return response.data;
}

export async function listVenues(): Promise<VenuePage> {
  const response = await httpClient.get<VenuePage>("/venues", { params: { page: 0, size: 100 } });
  return response.data;
}

export async function getVenueDay(venueId: string, date: string): Promise<VenueDay> {
  const response = await httpClient.get<VenueDay>(`/venues/${venueId}/days/${date}`);
  return response.data;
}

export async function bookVenueSlot(
  eventId: string,
  venueId: string,
  startsAt: string,
  endsAt: string,
): Promise<void> {
  await httpClient.put(`/events/${eventId}/slot`, { venueId, startsAt, endsAt });
}

export async function releaseVenueSlot(eventId: string): Promise<void> {
  await httpClient.delete(`/events/${eventId}/slot`);
}
