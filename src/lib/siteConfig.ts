import { safeGet, safeSet } from "./storage";

/** Everything the owner can change from the admin app. Data only: text, colours, order, image URLs. */
export type SectionType =
  | "hero" | "menu" | "steps" | "trips" | "buildings" | "support"
  | "text" | "image" | "button";

export interface SiteSection {
  id: string;
  type: SectionType;
  visible: boolean;
  title?: string;
  body?: string;
  headline?: string;
  image_url?: string | null;
  caption?: string;
  label?: string;
  to?: string;
  items?: string[];
}

export type FontChoice = "default" | "friendly" | "classic" | "modern";

export interface SiteConfig {
  v: 1;
  brand_name: string;
  theme: { bg: string; ink: string; accent: string; good: string; paper: string; font: FontChoice };
  banner: { enabled: boolean; text: string; tone: "info" | "promo" | "warn"; link_label: string; link_to: string };
  sections: SiteSection[];
  footer_note: string;
}

/** Must stay identical to the Android app's default and to how the site looked before customization. */
export const DEFAULT_SITE_CONFIG: SiteConfig = {
  v: 1,
  brand_name: "",
  theme: { bg: "#FFC93C", ink: "#1B1410", accent: "#D7263D", good: "#2B7A3D", paper: "#FFFDF7", font: "default" },
  banner: { enabled: false, text: "", tone: "info", link_label: "", link_to: "" },
  sections: [
    { id: "hero", type: "hero", visible: true, headline: "Hot crispy burgers at your dorm door.", image_url: null },
    { id: "menu", type: "menu", visible: true, title: "Menu" },
    {
      id: "steps", type: "steps", visible: true, title: "",
      items: [
        "Add burgers to your cart.",
        "Pick your delivery trip at checkout.",
        "Choose your college. KKB and KKC deliveries go to the college lobby.",
        "Pay cash on delivery or by DuitNow QR.",
        "Follow your delivery under My orders."
      ]
    },
    { id: "trips", type: "trips", visible: true, title: "Delivery trips", body: "Choose the delivery window that suits you. Orders stay open until that window ends." },
    { id: "buildings", type: "buildings", visible: true, body: "We deliver to" },
    { id: "support", type: "support", visible: true, body: "Problem with an order?", label: "Report it" }
  ],
  footer_note: ""
};

const HEX = /^#[0-9a-fA-F]{6}$/;
const IMG = /^https:\/\/[a-z0-9.-]+\/storage\/v1\/object\/public\/site-assets\/[0-9]{10,16}-[0-9a-f]{8}\.jpg$/;
const TYPES: SectionType[] = ["hero", "menu", "steps", "trips", "buildings", "support", "text", "image", "button"];
const FONTS: FontChoice[] = ["default", "friendly", "classic", "modern"];

const str = (v: unknown, max: number, fallback = ""): string =>
  typeof v === "string" ? v.slice(0, max) : fallback;
const color = (v: unknown, fallback: string): string =>
  typeof v === "string" && HEX.test(v) ? v.toUpperCase() : fallback;
const link = (v: unknown): string => {
  const s = str(v, 200);
  return /^\/($|[^/])/.test(s) || /^https:\/\/[^/\s]+/.test(s) ? s : "";
};
const image = (v: unknown): string | null => (typeof v === "string" && IMG.test(v) ? v : null);

/**
 * Defensive client-side copy. The database already validated the config; this guarantees a bad or
 * old value can never break the page (it falls back to the built-in design for that field).
 */
export function sanitizeSiteConfig(raw: unknown): SiteConfig {
  if (!raw || typeof raw !== "object") return DEFAULT_SITE_CONFIG;
  const r = raw as Record<string, unknown>;
  const t = (r.theme && typeof r.theme === "object" ? r.theme : {}) as Record<string, unknown>;
  const b = (r.banner && typeof r.banner === "object" ? r.banner : {}) as Record<string, unknown>;
  const d = DEFAULT_SITE_CONFIG;

  const sections: SiteSection[] = [];
  const seen = new Set<string>();
  if (Array.isArray(r.sections)) {
    for (const item of r.sections.slice(0, 24)) {
      if (!item || typeof item !== "object") continue;
      const s = item as Record<string, unknown>;
      const type = s.type as SectionType;
      const id = typeof s.id === "string" ? s.id : "";
      if (!TYPES.includes(type) || !/^[a-z0-9_]{1,24}$/.test(id) || seen.has(id)) continue;
      seen.add(id);
      sections.push({
        id, type, visible: s.visible !== false,
        title: str(s.title, 60), body: str(s.body, 600), headline: str(s.headline, 120),
        image_url: image(s.image_url), caption: str(s.caption, 120),
        label: str(s.label, 30), to: link(s.to),
        items: Array.isArray(s.items)
          ? s.items.filter((x): x is string => typeof x === "string").slice(0, 8).map((x) => x.slice(0, 160))
          : []
      });
    }
  }
  // The menu must always be reachable, whatever the stored value says.
  if (!sections.some((s) => s.type === "menu" && s.visible)) {
    return { ...d, theme: sanitizeTheme(t), brand_name: str(r.brand_name, 30) };
  }

  return {
    v: 1,
    brand_name: str(r.brand_name, 30),
    theme: sanitizeTheme(t),
    banner: {
      enabled: b.enabled === true,
      text: str(b.text, 120),
      tone: b.tone === "promo" || b.tone === "warn" ? b.tone : "info",
      link_label: str(b.link_label, 30),
      link_to: link(b.link_to)
    },
    sections,
    footer_note: str(r.footer_note, 160)
  };
}

function sanitizeTheme(t: Record<string, unknown>): SiteConfig["theme"] {
  const d = DEFAULT_SITE_CONFIG.theme;
  return {
    bg: color(t.bg, d.bg), ink: color(t.ink, d.ink), accent: color(t.accent, d.accent),
    good: color(t.good, d.good), paper: color(t.paper, d.paper),
    font: FONTS.includes(t.font as FontChoice) ? (t.font as FontChoice) : "default"
  };
}

const FONT_STACKS: Record<FontChoice, { display: string; sans: string } | null> = {
  default: null,
  friendly: { display: '"Figtree Variable", ui-sans-serif, system-ui, sans-serif', sans: '"Figtree Variable", ui-sans-serif, system-ui, sans-serif' },
  classic: { display: 'Georgia, "Times New Roman", serif', sans: '"Figtree Variable", ui-sans-serif, system-ui, sans-serif' },
  modern: { display: 'ui-sans-serif, system-ui, "Segoe UI", Roboto, sans-serif', sans: 'ui-sans-serif, system-ui, "Segoe UI", Roboto, sans-serif' }
};

/** Writes the theme into CSS variables. Tailwind utilities read these variables, so everything follows. */
export function applyTheme(theme: SiteConfig["theme"]): void {
  const root = document.documentElement.style;
  root.setProperty("--color-bun", theme.bg);
  root.setProperty("--color-ink", theme.ink);
  root.setProperty("--color-ketchup", theme.accent);
  root.setProperty("--color-pickle", theme.good);
  root.setProperty("--color-paper", theme.paper);
  const f = FONT_STACKS[theme.font];
  if (f) {
    root.setProperty("--font-display", f.display);
    root.setProperty("--font-sans", f.sans);
  } else {
    root.removeProperty("--font-display");
    root.removeProperty("--font-sans");
  }
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", theme.bg);
}

const CACHE_KEY = "cbms:siteConfig:v1";

export function readCachedSiteConfig(): SiteConfig | null {
  const raw = safeGet(CACHE_KEY);
  if (!raw) return null;
  try { return sanitizeSiteConfig(JSON.parse(raw)); } catch { return null; }
}

export function saveCachedSiteConfig(config: SiteConfig | null): void {
  safeSet(CACHE_KEY, config ? JSON.stringify(config) : "");
}
