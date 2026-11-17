interface ApiError { message?: string; hint?: string | null }

const MESSAGES: Record<string, (hint?: string | null) => string> = {
  SOLD_OUT: () => "Sold out for now. Check back at the next session.",
  INSUFFICIENT_STOCK: (h) => `Only ${h ?? "a few"} left. Lower the quantity and try again.`,
  NOT_ACCEPTING_ORDERS: () => "We are not taking orders right now.",
  TOO_MANY_ACTIVE_ORDERS: () => "You already have open orders. Wait for them to arrive first.",
  INVALID_NAME: () => "Check your name.",
  INVALID_PHONE: () => "Check your phone number.",
  INVALID_ROOM: () => "Check your room number.",
  INVALID_LOCATION: () => "That building and floor do not match. Pick them again.",
  TRIP_REQUIRED: () => "Choose a delivery trip.",
  INVALID_TRIP: () => "That trip is not available. Pick another.",
  TRIP_CLOSED: () => "That trip just closed. Pick another trip.",
  INVALID_NOTE: () => "Your note is too long. Keep it under 120 characters.",
  INVALID_QUANTITY: () => "Check the quantity.",
  NO_PRODUCT: () => "The menu is unavailable right now.",
  ORDER_NOT_FOUND: () => "We could not find that order. Check the link you were given.",
  ORDER_CANCELLED: () => "This order was cancelled.",
  PAYMENT_ALREADY_SETTLED: () => "Payment for this order is already confirmed.",
  RECEIPT_NOT_FOUND: () => "The upload did not finish. Try again.",
  INVALID_RECEIPT_PATH: () => "The upload could not be linked to your order. Try again.",
  CANNOT_CANCEL_ON_THE_WAY: () => "Your order is already on the way, so it cannot be cancelled now.",
  CANNOT_CANCEL_PAID: () => "This order is already paid or being checked. Message the team to cancel it.",
  NOT_A_QR_ORDER: () => "This order is paid in cash."
};

export function friendlyError(err: unknown): string {
  const e = err as ApiError | undefined;
  const code = e?.message ?? "";
  if (MESSAGES[code]) return MESSAGES[code](e?.hint);
  if (/Failed to fetch|NetworkError|Load failed/i.test(code)) return "No connection. Check your data or Wi-Fi and try again.";
  if (/exceeded the maximum allowed size|too large/i.test(code)) return "That image is too large. Choose a smaller screenshot.";
  if (/violates row-level security|Unauthorized/i.test(code)) return "This order can no longer accept a receipt.";
  return "Something went wrong. Try again in a moment.";
}
