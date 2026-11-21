import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "./ui";
import { Stepper } from "./Stepper";
import { useCart } from "../lib/cart";
import { formatRM } from "../lib/format";
import { subtotalCents } from "../lib/cartLogic";

export interface DrawerProduct {
  id: string;
  name: string;
  price_cents: number;
}

/** Bottom-sheet cart. `canCheckout` is false when ordering is closed or the menu is read-only. */
export function CartDrawer({
  products, maxQty, canCheckout, notice
}: { products: DrawerProduct[]; maxQty: number; canCheckout: boolean; notice?: string }) {
  const cart = useCart();
  const navigate = useNavigate();
  const panelRef = useRef<HTMLDivElement>(null);
  const open = cart.drawerOpen;

  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") cart.closeDrawer(); };
    document.addEventListener("keydown", onKey);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    panelRef.current?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
      previous?.focus?.();
    };
  }, [open, cart]);

  if (!open) return null;

  const priceOf = (id: string) => products.find((p) => p.id === id)?.price_cents;
  // Lines for products that no longer exist on the menu are shown as unavailable and excluded from the total.
  const lines = cart.lines;
  const total = subtotalCents(lines, priceOf);
  const orderable = lines.filter((l) => priceOf(l.productId) !== undefined);

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center" role="presentation">
      <button type="button" aria-label="Close cart" className="absolute inset-0 bg-ink/50" onClick={cart.closeDrawer} tabIndex={-1} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
        tabIndex={-1}
        className="relative max-h-[85vh] w-full max-w-md overflow-y-auto rounded-t-3xl border-2 border-b-0 border-ink bg-paper p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] outline-none"
      >
        <div className="flex items-center justify-between gap-3">
          <h2 id="cart-title" className="font-display text-2xl font-extrabold">Your cart</h2>
          <button type="button" onClick={cart.closeDrawer}
            className="min-h-11 rounded-full border-2 border-ink px-4 py-2 text-sm font-semibold">Close</button>
        </div>

        {lines.length === 0 ? (
          <div className="mt-5 rounded-xl border-2 border-dashed border-ink p-5 text-center">
            <p className="font-bold">Your cart is empty</p>
            <p className="mt-1 text-sm text-muted">Add something from the menu.</p>
          </div>
        ) : (
          <ul className="mt-4 space-y-3">
            {lines.map((l) => {
              const p = products.find((x) => x.id === l.productId);
              return (
                <li key={l.productId} className="rounded-xl border-2 border-ink bg-white p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-display text-lg font-extrabold">{p?.name ?? "Item no longer available"}</p>
                      {p && <p className="text-sm text-muted">{formatRM(p.price_cents)} each</p>}
                    </div>
                    <button type="button" onClick={() => cart.remove(l.productId)}
                      className="min-h-10 rounded-lg px-3 text-sm font-bold text-ketchup underline underline-offset-2">Remove</button>
                  </div>
                  {p && (
                    <div className="mt-3 flex items-center justify-between gap-3">
                      <Stepper value={l.quantity} min={1} max={maxQty} label="quantity"
                        onChange={(q) => cart.setQuantity(l.productId, q, maxQty)} />
                      <span className="font-bold">{formatRM(p.price_cents * l.quantity)}</span>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        {lines.length > 0 && (
          <div className="mt-4 space-y-3">
            <div className="flex justify-between border-t-2 border-ink pt-3 text-base">
              <span className="font-bold">Total</span>
              <span className="font-display text-2xl font-extrabold">{formatRM(total)}</span>
            </div>
            {notice && <p className="text-sm font-semibold text-ketchup">{notice}</p>}
            <Button
              disabled={!canCheckout || orderable.length === 0}
              onClick={() => { cart.closeDrawer(); navigate("/order"); }}
            >
              Checkout · {formatRM(total)}
            </Button>
            <button type="button"
              onClick={() => { if (window.confirm("Remove everything from your cart?")) cart.clear(); }}
              className="min-h-11 w-full rounded-xl border-2 border-ketchup bg-paper px-4 py-2.5 text-sm font-bold text-ketchup">
              Clear cart
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
