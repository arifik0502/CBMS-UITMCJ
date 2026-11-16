import type { Trip } from "./types";

const malaysiaClock = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kuala_Lumpur",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23"
});

export function tripHasEndedToday(trip: Trip, now: Date): boolean {
  if (typeof trip.end_time !== "string") return false;
  const [endHour, endMinute] = trip.end_time.split(":").map(Number);
  if (!Number.isInteger(endHour) || !Number.isInteger(endMinute)) return false;

  const parts = malaysiaClock.formatToParts(now);
  const hour = Number(parts.find((part) => part.type === "hour")?.value);
  const minute = Number(parts.find((part) => part.type === "minute")?.value);
  if (!Number.isInteger(hour) || !Number.isInteger(minute)) return false;

  return hour * 60 + minute >= endHour * 60 + endMinute;
}

export function tripIsSelectable(trip: Trip, now: Date): boolean {
  return trip.closed_reason === null && !tripHasEndedToday(trip, now);
}
