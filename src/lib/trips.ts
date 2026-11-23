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

export function tripIsSelectable(trip: Trip, now: Date): boolean {
  return trip.closed_reason === null && !tripHasEndedToday(trip, now);
}

export function tripDurationMinutes(trip: Trip): number {
  const start = timeMinutes(trip.start_time);
  const end = timeMinutes(trip.end_time);
  if (start === null || end === null || start === end) return 0;
  return end > start ? end - start : (24 * 60 - start) + end;
}
