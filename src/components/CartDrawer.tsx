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
  available_stock: number;
  image_url?: string | null;
}

/** Bottom-sheet cart. maxQty is the maximum total quantity allowed per order. */
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
  const lines = cart.lines;
  const total = subtotalCents(lines, priceOf);
  const totalQty = cart.count;
  const knownLines = lines.filter((l) => products.some((p) => p.id === l.productId));
  const missingProduct = knownLines.length !== lines.length;
  const stockProblem = knownLines.some((l) => {
    const p = products.find((x) => x.id === l.productId);
    return !p || p.available_stock < l.quantity;
  });
  const tooMany = totalQty > maxQty;
  const checkoutReady = canCheckout && knownLines.length > 0 && !missingProduct && !stockProblem && !tooMany;

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center" role="presentation">
      <button type="button" aria-label="Close cart" className="absolute inset-0 bg-ink/50" onClick={cart.closeDrawer} tabIndex={-1} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
        tabIndex={-1}
        className="relative shadow-2xl max-h-[85dvh] w-full max-w-md overflow-y-auto rounded-t-3xl border border-b-0 border-line bg-paper p-5 pb-[max(1.25rem,env(safe-area-inset-bottom))] outline-none"
      >
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 id="cart-title" className="font-display text-2xl font-extrabold">Your cart</h2>
            <p className="text-xs text-muted">{totalQty}/{maxQty} items allowed</p>
          </div>
          <button type="button" onClick={cart.closeDrawer}
            className="min-h-11 rounded-full border-2 border-ink px-4 py-2 text-sm font-semibold">Close</button>
        </div>

        {lines.length === 0 ? (
          <div className="mt-5 rounded-xl border border-dashed border-muted p-5 text-center">
            <p className="font-bold">Your cart is empty</p>
            <p className="mt-1 text-sm text-muted">Add something from the menu.</p>
          </div>
        ) : (
          <ul className="mt-4 space-y-3">
            {lines.map((l) => {
              const p = products.find((x) => x.id === l.productId);
              const otherQty = totalQty - l.quantity;
              const lineMax = p ? Math.max(1, Math.min(p.available_stock, maxQty - otherQty)) : 1;
              return (
                <li key={l.productId} className="rounded-xl border border-line bg-white p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex min-w-0 items-start gap-3">
                      {p?.image_url && <img src={p.image_url} alt="" className="size-16 shrink-0 rounded-lg border border-line object-cover" />}
                      <div>
                        <p className="font-display text-lg font-extrabold">{p?.name ?? "Item no longer available"}</p>
                        {p && <p className="text-sm text-muted">{formatRM(p.price_cents)} each · {p.available_stock} in stock</p>}
                      </div>
                    </div>
                    <button type="button" onClick={() => cart.remove(l.productId)}
                      className="min-h-10 shrink-0 rounded-lg px-2 text-sm font-bold text-ketchup underline underline-offset-2">Remove</button>
                  </div>
                  {p && (
                    <div className="mt-3 flex items-center justify-between gap-3">
                      <Stepper value={l.quantity} min={1} max={lineMax} label={p.name + " quantity"}
                        onChange={(q) => cart.setQuantity(l.productId, q, lineMax)} />
                      <span className="font-bold">{formatRM(p.price_cents * l.quantity)}</span>
                    </div>
                  )}
                  {p && l.quantity > p.available_stock && (
                    <p className="mt-2 text-sm font-semibold text-ketchup">Only {p.available_stock} currently available.</p>
                  )}
                </li>
              );
            })}
          </ul>
        )}

        {lines.length > 0 && (
          <div className="mt-4 space-y-3">
            <div className="flex justify-between border-t border-line pt-3 text-base">
              <span className="font-bold">Total</span>
              <span className="font-display text-2xl font-extrabold">{formatRM(total)}</span>
            </div>
            {tooMany && <p className="text-sm font-semibold text-ketchup">Remove {totalQty - maxQty} item{totalQty - maxQty === 1 ? "" : "s"} to continue.</p>}
            {stockProblem && <p className="text-sm font-semibold text-ketchup">One or more items exceed current stock. Reduce the quantity to continue.</p>}
            {missingProduct && <p className="text-sm font-semibold text-ketchup">Remove unavailable items before checkout.</p>}
            {notice && <p className="text-sm font-semibold text-ketchup">{notice}</p>}
            <Button
              disabled={!checkoutReady}
              onClick={() => { cart.closeDrawer(); navigate("/order"); }}
            >
              Checkout · {formatRM(total)}
            </Button>
            <button type="button"
              onClick={() => { if (window.confirm("Remove everything from your cart?")) cart.clear(); }}
              className="min-h-11 w-full rounded-xl border border-ketchup bg-paper px-4 py-2.5 text-sm font-bold text-ketchup">
              Clear cart
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
