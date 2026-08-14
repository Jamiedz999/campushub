import { httpClient } from "../../../lib/httpClient";
import type { AttendanceRoster, CheckInResult, DoorCode } from "../types";

/** The rotating code for the Officer's door screen. 404 unless the caller is this Club's Officer. */
export async function getDoorCode(eventId: string): Promise<DoorCode> {
  const response = await httpClient.get<DoorCode>(`/events/${eventId}/door-code`);
  return response.data;
}

/** The Roster with attendance against it — the manual override's list. Officer-only. */
export async function getAttendanceRoster(eventId: string): Promise<AttendanceRoster> {
  const response = await httpClient.get<AttendanceRoster>(`/events/${eventId}/attendance`);
  return response.data;
}

/**
 * The scan. The Student is identified by their session, never by anything in this body: the code
 * proves presence, the session proves identity, and neither half alone admits anyone.
 */
export async function checkIn(eventId: string, token: string): Promise<CheckInResult> {
  const response = await httpClient.post<CheckInResult>(`/events/${eventId}/attendance`, { token });
  return response.data;
}

/** The Officer's override for a failed phone or a dead screen. Idempotent. */
export async function markPresent(eventId: string, studentId: string): Promise<void> {
  await httpClient.put(`/events/${eventId}/attendance/${studentId}`);
}
