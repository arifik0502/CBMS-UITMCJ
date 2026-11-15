import { useCallback, useEffect, useRef, useState } from "react";
import { getOrder } from "./api";
import { friendlyError } from "./errors";
import type { OrderView } from "./types";

/** Loads an order by tracking token and polls while it is still in progress. */
export function useOrder(token: string | undefined, pollMs = 8000) {
  const [order, setOrder] = useState<OrderView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const timer = useRef<number | undefined>(undefined);

  const load = useCallback(async (silent = false) => {
    if (!token) return;
    if (!silent) setLoading(true);
    try {
      setOrder(await getOrder(token));
      setError(null);
    } catch (e) {
      // keep showing the last known order on a flaky poll
      if (!silent) setError(friendlyError(e));
    } finally {
      if (!silent) setLoading(false);
    }
  }, [token]);

  useEffect(() => { void load(); }, [load]);

  const done = order?.order_status === "DELIVERED" || order?.order_status === "CANCELLED";
  useEffect(() => {
    if (!token || done) return;
    const tick = () => { if (!document.hidden) void load(true); };
    timer.current = window.setInterval(tick, pollMs);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer.current);
      document.removeEventListener("visibilitychange", tick);
    };
  }, [token, done, pollMs, load]);

  return { order, error, loading, reload: () => load() };
}
