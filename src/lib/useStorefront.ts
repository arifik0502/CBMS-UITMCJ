import { useCallback, useEffect, useState } from "react";
import { getStorefront } from "./api";
import { friendlyError } from "./errors";
import type { Storefront } from "./types";

export function useStorefront() {
  const [data, setData] = useState<Storefront | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getStorefront());
    } catch (e) {
      setError(friendlyError(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return { data, error, loading, reload: load };
}
