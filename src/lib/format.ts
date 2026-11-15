/** Integer cents -> "RM6.50". Pure integer maths, no floating point. */
export function formatRM(cents: number): string {
  const sign = cents < 0 ? "-" : "";
  const abs = Math.abs(Math.trunc(cents));
  return `${sign}RM${Math.trunc(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-MY", { hour: "numeric", minute: "2-digit" });
}

/** "TS-205": building code + typed room number. */
export function roomLabel(building: string, room: string): string {
  return `${building}-${room}`;
}
