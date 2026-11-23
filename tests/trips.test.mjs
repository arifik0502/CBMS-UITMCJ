import test from "node:test";
import assert from "node:assert/strict";
import {
  tripClosedReason,
  tripDurationMinutes,
  tripEndsNextDay,
  tripIsSelectable,
  tripOrderDeadline
} from "../src/lib/trips.ts";

const midnightTrip = {
  id: "overnight",
  trip_no: 4,
  label: "Trip Midnight Snack",
  start_time: "23:15:00",
  end_time: "02:00:00",
  start_label: "11:15 PM",
  end_label: "2:00 AM",
  closes_label: "2:00 AM",
  closed_reason: null
};

const malaysiaTime = (day, time) => new Date(day + "T" + time + "+08:00");

test("overnight trip runs across midnight and has the correct duration", () => {
  assert.equal(tripEndsNextDay(midnightTrip), true);
  assert.equal(tripDurationMinutes(midnightTrip), 165);
});

test("overnight trip remains orderable before midnight", () => {
  const now = malaysiaTime("2026-11-24", "23:30:00");
  assert.equal(tripIsSelectable(midnightTrip, now), true);
  assert.equal(tripOrderDeadline(midnightTrip, now), "Order until 2:00 AM tomorrow");
});

test("overnight trip remains orderable after midnight until 2 AM", () => {
  const now = malaysiaTime("2026-11-25", "01:30:00");
  assert.equal(tripIsSelectable(midnightTrip, now), true);
  assert.equal(tripOrderDeadline(midnightTrip, now), "Order until 2:00 AM today");
});

test("after 2 AM, the next occurrence is tonight, not ended for the whole day", () => {
  const now = malaysiaTime("2026-11-25", "03:15:00");
  assert.equal(tripClosedReason(midnightTrip, now), null);
  assert.equal(tripOrderDeadline(midnightTrip, now), "Order until 2:00 AM tomorrow");
});

test("stale backend ENDED flags do not close a new overnight occurrence", () => {
  const now = malaysiaTime("2026-11-25", "00:01:00");
  assert.equal(tripClosedReason({ ...midnightTrip, closed_reason: "ENDED" }, now), null);
  assert.equal(tripIsSelectable({ ...midnightTrip, closed_reason: "ENDED" }, now), true);
});

test("explicit team closures and cutoffs still win", () => {
  const now = malaysiaTime("2026-11-24", "23:30:00");
  assert.equal(tripIsSelectable({ ...midnightTrip, closed_reason: "TEAM" }, now), false);
  assert.equal(tripIsSelectable({ ...midnightTrip, closed_reason: "CUTOFF" }, now), false);
});

test("same-day trips end on time and reopen for the next date", () => {
  const trip = {
    ...midnightTrip,
    start_time: "19:00:00",
    end_time: "19:30:00",
    start_label: "7:00 PM",
    end_label: "7:30 PM"
  };
  assert.equal(tripClosedReason(trip, malaysiaTime("2026-11-24", "20:00:00")), "ENDED");
  assert.equal(tripIsSelectable({ ...trip, closed_reason: "ENDED" }, malaysiaTime("2026-11-25", "00:30:00")), true);
  assert.equal(tripOrderDeadline(trip, malaysiaTime("2026-11-25", "00:30:00")), "Order until 7:30 PM");
});
