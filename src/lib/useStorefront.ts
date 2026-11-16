import { useCallback, useEffect, useState } from "react";
import { getStorefront } from "./api";
import { friendlyError } from "./errors";
import type { Storefront } from "./types";

export function useStorefront() {
  const [data, setData] = useState<Storefront | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (silent = false) => {
    if (!silent) { setLoading(true); setError(null); }
    try {
      setData(await getStorefront());
    } catch (e) {
      if (!silent) setError(friendlyError(e));
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    // keep stock and trip cutoffs fresh while the page stays open
    const id = window.setInterval(() => { if (!document.hidden) void load(true); }, 45000);
    return () => window.clearInterval(id);
  }, [load]);

  return { data, error, loading, reload: () => load() };
}
