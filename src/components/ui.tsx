import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Link } from "react-router-dom";
import { useSiteConfig } from "../lib/SiteConfigContext";

/** Owner-controlled announcement strip (set from the admin app). */
function Banner() {
  const { banner } = useSiteConfig();
  if (!banner.enabled || !banner.text) return null;
  const tones = { info: "bg-paper", promo: "bg-bun-deep", warn: "bg-[#ffe3e0]" } as const;
  const external = /^https:\/\//.test(banner.link_to);
  return (
    <div role="status" className={`mb-2 rounded-xl border-2 border-ink px-4 py-2.5 text-center text-sm font-bold ${tones[banner.tone]}`}>
      {banner.text}
      {banner.link_label && banner.link_to && (
        <>
          {" "}
          {external
            ? <a href={banner.link_to} target="_blank" rel="noopener noreferrer" className="underline">{banner.link_label}</a>
            : <Link to={banner.link_to} className="underline">{banner.link_label}</Link>}
        </>
      )}
    </div>
  );
}

export function Shell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className={`mx-auto w-full ${wide ? "max-w-6xl" : "max-w-xl"} px-4 sm:px-6 pb-28 pt-[max(1rem,env(safe-area-inset-top))]`}>
      <Banner />
      {children}
    </div>
  );
}

export function Header({ title, back }: { title?: string; back?: string }) {
  const { brand_name } = useSiteConfig();
  return (
    <header className="masthead">
      <Link to="/" className="wordmark">
        {brand_name || "Crispy Burger"}
      </Link>
      {back ? (
        <Link to={back} className="nav-link">
          <span aria-hidden="true">←</span> Back
        </Link>
      ) : title ? (
        <span className="text-sm font-semibold">{title}</span>
      ) : null}
    </header>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary";
  busy?: boolean;
};

export function Button({ variant = "primary", busy, className = "", children, disabled, ...rest }: BtnProps) {
  return (
    <button className={`action action-${variant} w-full ${className}`} disabled={disabled || busy} {...rest}>
      {busy && <Spinner />}
      {children}
    </button>
  );
}

export function LinkButton({ to, children, variant = "primary" }: { to: string; children: ReactNode; variant?: "primary" | "secondary" }) {
  return (
    <Link
      to={to}
      className={`action action-${variant} w-full`}
    >
      {children}
    </Link>
  );
}

export function Spinner() {
  return (
    <span
      role="status"
      aria-label="Loading"
      className="inline-block size-4 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none"
    />
  );
}

export function Field({
  label, htmlFor, error, hint, children
}: { label: string; htmlFor: string; error?: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1.5 block text-sm font-bold">
        {label}
      </label>
      {children}
      {hint && !error && <p className="mt-1 text-sm text-muted">{hint}</p>}
      {error && (
        <p id={`${htmlFor}-error`} role="alert" className="mt-1 text-sm font-semibold text-ketchup">
          {error}
        </p>
      )}
    </div>
  );
}

export const inputClass =
  "min-h-12 w-full rounded-xl border border-muted bg-white px-3.5 py-2.5 text-base placeholder:text-muted/70 aria-[invalid=true]:border-ketchup disabled:opacity-50";

export function Notice({ tone = "info", children }: { tone?: "info" | "error" | "success"; children: ReactNode }) {
  const tones = {
    info: "bg-paper",
    error: "bg-[#ffe3e0]",
    success: "bg-[#dff3df]"
  } as const;
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`rounded-xl border border-line border-l-4 border-l-current px-4 py-3 text-sm font-semibold ${tones[tone]}`}>
      {children}
    </div>
  );
}

export function LoadingScreen({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-24 font-semibold">
      <Spinner /> {label}
    </div>
  );
}

export function ErrorScreen({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="space-y-4 py-16 text-center">
      <p className="font-display text-2xl font-extrabold">That did not load</p>
      <p className="text-sm font-medium">{message}</p>
      {onRetry && (
        <div className="mx-auto max-w-48">
          <Button onClick={onRetry}>Try again</Button>
        </div>
      )}
    </div>
  );
}

export function Pill({ tone, children }: { tone: "good" | "warn" | "bad" | "neutral"; children: ReactNode }) {
  const tones = {
    good: "bg-pickle/15 text-ink",
    warn: "bg-bun/40 text-ink",
    bad: "bg-ketchup/15 text-ink",
    neutral: "bg-line/60 text-ink"
  } as const;
  return (
    <span className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-bold ${tones[tone]}`}>
      {children}
    </span>
  );
}
