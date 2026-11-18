import { useCallback, useEffect, useState } from "react";
import { getStorefront } from "./api";
import { friendlyError } from "./errors";
import { readPublicMenu, savePublicMenu, toReadOnlyStorefront } from "./storefrontCache";
import type { Storefront } from "./types";

export function useStorefront() {
  const [data, setData] = useState<Storefront | null>(() => {
    const cached = readPublicMenu();
    return cached ? toReadOnlyStorefront(cached) : null;
  });
  const [cachedOnly, setCachedOnly] = useState(() => Boolean(readPublicMenu()));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(() => !readPublicMenu());

  const load = useCallback(async (silent = false) => {
    if (!silent && !cachedOnly) { setLoading(true); setError(null); }
    try {
      const fresh = await getStorefront();
      savePublicMenu(fresh);
      setData(fresh);
      setCachedOnly(false);
      setError(null);
    } catch (e) {
      const cached = readPublicMenu();
      if (cached) {
        setData(toReadOnlyStorefront(cached));
        setCachedOnly(true);
        setError(null);
      } else if (!silent) {
        setError(friendlyError(e));
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [cachedOnly]);

  useEffect(() => {
    void load();
    // Revalidate live stock and delivery availability while the page is visible.
    const id = window.setInterval(() => {
      if (!document.hidden) void load(true);
    }, 45000);
    const onOnline = () => { void load(); };
    window.addEventListener("online", onOnline);
    return () => {
      window.clearInterval(id);
      window.removeEventListener("online", onOnline);
    };
  }, [load]);

  return { data, error, loading, cachedOnly, reload: () => load() };
}
