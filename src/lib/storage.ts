// localStorage can throw (private mode, blocked). Never let that break ordering.
export function safeGet(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
export function safeSet(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* ignore */ }
}
export function safeSessionGet(key: string): string | null {
  try { return sessionStorage.getItem(key); } catch { return null; }
}
export function safeSessionSet(key: string, value: string): void {
  try { sessionStorage.setItem(key, value); } catch { /* ignore */ }
}
export function safeSessionRemove(key: string): void {
  try { sessionStorage.removeItem(key); } catch { /* ignore */ }
}
export const LAST_ORDER_KEY = "cbms:lastOrderToken";
export const IDEMPOTENCY_KEY = "cbms:idempotencyKey";
