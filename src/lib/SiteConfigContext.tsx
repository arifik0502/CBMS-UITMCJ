import { createContext, useContext, useEffect, useLayoutEffect, useState, type ReactNode } from "react";
import { supabase } from "./supabase";
import {
  applyTheme, DEFAULT_SITE_CONFIG, readCachedSiteConfig, sanitizeSiteConfig, saveCachedSiteConfig,
  type SiteConfig
} from "./siteConfig";

const SiteConfigContext = createContext<SiteConfig>(DEFAULT_SITE_CONFIG);

/** The admin app opens the site as `?preview=app` and pushes its unsaved draft in. Only affects that one view. */
const isPreview = () => new URLSearchParams(window.location.search).get("preview") === "app";

declare global {
  interface Window { __cbmsPreview?: (config: unknown) => void }
}

export function SiteConfigProvider({ children }: { children: ReactNode }) {
  const preview = isPreview();
  const [config, setConfig] = useState<SiteConfig>(() => (preview ? DEFAULT_SITE_CONFIG : readCachedSiteConfig() ?? DEFAULT_SITE_CONFIG));

  useLayoutEffect(() => { applyTheme(config.theme); }, [config.theme]);

  useEffect(() => {
    if (preview) {
      window.__cbmsPreview = (raw) => setConfig(sanitizeSiteConfig(typeof raw === "string" ? safeParse(raw) : raw));
      return () => { delete window.__cbmsPreview; };
    }
    let cancelled = false;
    const load = async () => {
      try {
        const { data, error } = await supabase.rpc("get_site_config");
        if (error || cancelled) return;
        const raw = (data as { config: unknown } | null)?.config ?? null;
        if (!raw) { saveCachedSiteConfig(null); setConfig(DEFAULT_SITE_CONFIG); return; }
        const clean = sanitizeSiteConfig(raw);
        saveCachedSiteConfig(clean);
        setConfig(clean);
      } catch { /* keep the cached or built-in design */ }
    };
    void load();
    const id = window.setInterval(() => { if (!document.hidden) void load(); }, 120000);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [preview]);

  return <SiteConfigContext.Provider value={config}>{children}</SiteConfigContext.Provider>;
}

function safeParse(s: string): unknown {
  try { return JSON.parse(s); } catch { return null; }
}

export const useSiteConfig = () => useContext(SiteConfigContext);
