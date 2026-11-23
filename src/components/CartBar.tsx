import { formatRM } from "../lib/format";

/** Sticky bottom bar (Shopee / Foodpanda style). Hidden when the cart is empty. */
export function CartBar({
  count, totalCents, onOpen
}: { count: number; totalCents: number; onOpen: () => void }) {
  if (count <= 0) return null;
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 px-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
      <button
        type="button"
        onClick={onOpen}
        className="mx-auto flex min-h-14 w-full max-w-md items-center justify-between gap-3 rounded-full bg-ink px-5 py-3 text-left text-paper shadow-xl active:translate-y-0.5"
      >
        <span className="flex items-center gap-3">
          <span className="grid size-8 place-items-center rounded-full bg-bun text-sm font-extrabold text-ink" aria-hidden="true">{count}</span>
          <span className="font-bold">View cart<span className="sr-only"> ({count} item{count === 1 ? "" : "s"})</span></span>
        </span>
        <span className="font-display text-lg font-extrabold">{formatRM(totalCents)}</span>
      </button>
    </div>
  );
}
