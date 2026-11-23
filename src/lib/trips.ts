import type { Trip } from "./types";

const malaysiaClock = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kuala_Lumpur",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23"
});

function timeMinutes(value: string): number | null {
  const [hour, minute] = value.split(":").map(Number);
  if (!Number.isInteger(hour) || !Number.isInteger(minute)) return null;
  if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  return hour * 60 + minute;
}

function malaysiaMinutes(now: Date): number | null {
  const parts = malaysiaClock.formatToParts(now);
  const hour = Number(parts.find((part) => part.type === "hour")?.value);
  const minute = Number(parts.find((part) => part.type === "minute")?.value);
  if (!Number.isInteger(hour) || !Number.isInteger(minute)) return null;
  return hour * 60 + minute;
}

export function tripEndsNextDay(trip: Trip): boolean {
  const start = timeMinutes(trip.start_time);
  const end = timeMinutes(trip.end_time);
  return start !== null && end !== null && end < start;
}

export function tripHasEndedToday(trip: Trip, now: Date): boolean {
  const start = timeMinutes(trip.start_time);
  const end = timeMinutes(trip.end_time);
  const current = malaysiaMinutes(now);
  if (start === null || end === null || current === null) return false;
  if (start === end) return true;

  // Overnight trips (for example 11:00 PM -> 2:15 AM) are recurring by
  // their start date. After 2:15 AM, the next occurrence is tonight, so it
  // remains selectable instead of being incorrectly marked ended all day.
  if (end < start) return false;

  return current >= end;
}

/**
 * Server trip statuses are fetched periodically. ENDED can be stale just
 * after midnight; derive the current daily-window status from Malaysia time.
 * Manual team closures and cutoffs are always respected.
 */
export function tripClosedReason(trip: Trip, now: Date): Trip["closed_reason"] {
  if (trip.closed_reason === "TEAM" || trip.closed_reason === "CUTOFF") {
    return trip.closed_reason;
  }
  return tripHasEndedToday(trip, now) ? "ENDED" : null;
}

export function tripIsSelectable(trip: Trip, now: Date): boolean {
  return tripClosedReason(trip, now) === null;
}

/** For overnight windows, show whether their 2 AM end is today or tomorrow. */
export function overnightEndDay(trip: Trip, now: Date): "today" | "tomorrow" | null {
  if (!tripEndsNextDay(trip)) return null;
  const end = timeMinutes(trip.end_time);
  const current = malaysiaMinutes(now);
  if (end === null || current === null) return null;
  return current < end ? "today" : "tomorrow";
}

export function tripOrderDeadline(trip: Trip, now: Date): string {
  const day = overnightEndDay(trip, now);
  return "Order until " + trip.end_label + (day ? " " + day : "");
}

export function tripDurationMinutes(trip: Trip): number {
  const start = timeMinutes(trip.start_time);
  const end = timeMinutes(trip.end_time);
  if (start === null || end === null || start === end) return 0;
  return end > start ? end - start : (24 * 60 - start) + end;
}
