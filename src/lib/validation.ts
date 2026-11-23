// Mirrors the server rules in create_order(). The server is the authority; this only gives fast feedback.
export const PHONE_RE = /^(\+?60|0)1[0-9]{8,9}$/;
export const NOTE_MAX = 120;
export const ROOM_RE = /^[A-Z0-9][A-Z0-9 /-]{0,9}$/;

export function normalizePhone(v: string): string {
  return v.replace(/[\s-]/g, "");
}
export function normalizeRoom(v: string): string {
  return v.trim().toUpperCase();
}

export interface OrderFormValues {
  name: string;
  phone: string;
  buildingId: string;
  floor: string;
  room: string;
  quantity: number;
  method: "CASH" | "DUITNOW" | "";
  tripId: string;
  custom: boolean;
  note: string;
}

export type FormErrors = Partial<Record<keyof OrderFormValues, string>>;

export function validateOrder(v: OrderFormValues, maxQty: number): FormErrors {
  const e: FormErrors = {};
  const name = v.name.trim();
  if (name.length < 2) e.name = "Enter your name.";
  else if (name.length > 80) e.name = "Name is too long.";
  if (!PHONE_RE.test(normalizePhone(v.phone))) e.phone = "Enter a Malaysian mobile number, e.g. 012-345 6789.";
  if (!v.buildingId) e.buildingId = "Choose your building.";
  if (!v.floor) e.floor = "Choose your floor.";
  if (!ROOM_RE.test(normalizeRoom(v.room))) e.room = "Enter your room number, e.g. 205 (letters and numbers only).";
  if (!Number.isInteger(v.quantity) || v.quantity < 1 || v.quantity > maxQty) e.quantity = `Choose 1 to ${maxQty} items.`;
  if (!v.method) e.method = "Choose how you will pay.";
  if (!v.tripId) e.tripId = "Choose a delivery trip.";
  if (v.custom) {
    const note = v.note.trim();
    if (note.length < 2) e.note = "Tell the kitchen what you want, e.g. no sauce.";
    else if (note.length > NOTE_MAX) e.note = `Keep your note under ${NOTE_MAX} characters.`;
  }
  return e;
}
export const TICKET_MESSAGE_MIN = 5;
export const TICKET_MESSAGE_MAX = 1000;

export interface TicketFormValues {
  message: string;
  name: string;
  phone: string;
}
export type TicketFormErrors = Partial<Record<keyof TicketFormValues, string>>;

/** Mirrors create_support_ticket(); the server is still the authority. */
export function validateTicket(v: TicketFormValues): TicketFormErrors {
  const e: TicketFormErrors = {};
  const message = v.message.trim();
  if (message.length < TICKET_MESSAGE_MIN) e.message = "Tell us what went wrong (at least 5 characters).";
  else if (message.length > TICKET_MESSAGE_MAX) e.message = `Keep it under ${TICKET_MESSAGE_MAX} characters.`;
  const name = v.name.trim();
  if (name.length < 2) e.name = "Enter your name.";
  else if (name.length > 80) e.name = "Name is too long.";
  if (!PHONE_RE.test(normalizePhone(v.phone))) e.phone = "Enter a Malaysian mobile number, e.g. 012-345 6789.";
  return e;
}

