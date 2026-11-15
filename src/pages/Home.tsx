import { Link } from "react-router-dom";
import { ErrorScreen, LinkButton, LoadingScreen, Pill, Shell } from "../components/ui";
import { formatRM } from "../lib/format";
import { LAST_ORDER_KEY, safeGet } from "../lib/storage";
import { useStorefront } from "../lib/useStorefront";

export default function Home() {
  const { data, error, loading, reload } = useStorefront();
  const lastToken = safeGet(LAST_ORDER_KEY);

  if (loading) return <Shell><LoadingScreen label="Loading menu" /></Shell>;
  if (error || !data) return <Shell><ErrorScreen message={error ?? "No data."} onRetry={reload} /></Shell>;

  const p = data.product;
  const soldOut = data.available_stock <= 0;
  const canOrder = data.accepting_orders && p !== null && !soldOut;
  const low = data.available_stock > 0 && data.available_stock <= 10;

  return (
    <Shell>
      <header className="flex items-center justify-between py-3">
        <span className="font-display text-xl font-extrabold tracking-tight">{data.business_name}</span>
        {lastToken && (
          <Link to={`/track/${lastToken}`} className="min-h-11 rounded-full border-2 border-ink bg-paper px-4 py-2 text-sm font-bold">
            My order
          </Link>
        )}
      </header>

      <section className="pt-4 text-center">
        <h1 className="font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-tight">
          Hot crispy burgers at your dorm door.
        </h1>
        <div className="relative mx-auto mt-6 max-w-xs">
          <img src="/burger.svg" alt="Crispy chicken burger" width={400} height={360} className="burger-drop w-full" />
          {p && (
            <div
              className="absolute -right-1 top-2 grid size-24 rotate-12 place-items-center rounded-full border-[2.5px] border-ink bg-ketchup text-center font-display text-xl font-extrabold leading-tight text-white shadow-[3px_3px_0_0_var(--color-ink)]"
              aria-label={`Price ${formatRM(p.price_cents)} each`}
            >
              {formatRM(p.price_cents)}
              <span className="block text-xs font-bold">each</span>
            </div>
          )}
        </div>
      </section>

      <section className="mt-6 space-y-4">
        <div className="flex items-center justify-center gap-2">
          {!data.accepting_orders ? (
            <Pill tone="bad">Not taking orders</Pill>
          ) : soldOut ? (
            <Pill tone="bad">Sold out</Pill>
          ) : low ? (
            <Pill tone="warn">Only {data.available_stock} left</Pill>
          ) : (
            <Pill tone="good">In stock</Pill>
          )}
        </div>
        {canOrder ? (
          <LinkButton to="/order">Order now</LinkButton>
        ) : (
          <p className="text-center text-sm font-semibold">
            {soldOut ? "We have sold out this session. Check back next time." : "Ordering is closed right now."}
          </p>
        )}
      </section>

      <section className="ticket mt-8">
        <div className="p-5">
          <h2 className="font-display text-xl font-extrabold">{p?.name ?? "Crispy Chicken Burger"}</h2>
          <p className="mt-1 text-sm text-muted">{p?.description}</p>
        </div>
        <div className="tear" />
        <ol className="space-y-3 p-5 text-sm font-medium">
          <li><strong>1.</strong> Pick how many burgers.</li>
          <li><strong>2.</strong> Tell us your building, floor and room.</li>
          <li><strong>3.</strong> Pay cash on delivery or by DuitNow QR.</li>
          <li><strong>4.</strong> We bring it to your room.</li>
        </ol>
        {data.buildings.length > 0 && (
          <>
            <div className="tear" />
            <p className="p-5 text-sm">
              We deliver to <strong>{data.buildings.map((b) => b.code).join(", ")}</strong>.
            </p>
          </>
        )}
      </section>
    </Shell>
  );
}
