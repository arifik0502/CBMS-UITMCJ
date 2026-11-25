import { useEffect, useState } from "react";
import { getOrderEditOptions, editBookedOrder } from "../lib/api";
import { friendlyError } from "../lib/errors";
import { formatRM } from "../lib/format";
import type { OrderEditOptions, OrderView } from "../lib/types";
import { Button, Notice } from "./ui";

export default function EditOrder({ token, onSaved, initiallyOpen = false }: {
  token: string; onSaved: (order: OrderView) => void; initiallyOpen?: boolean;
}) {
  const [open, setOpen] = useState(initiallyOpen);
  const [options, setOptions] = useState<OrderEditOptions | null>(null);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setOptions(null);
    setError(null);
    getOrderEditOptions(token).then((data) => {
      if (!active) return;
      setOptions(data);
      setQuantities(Object.fromEntries(data.products.map(p => [p.id, p.quantity])));
      setNote(data.note ?? "");
    }).catch((e) => { if (active) setError(friendlyError(e)); });
    return () => { active = false; };
  }, [open, token, attempt]);

  const count = Object.values(quantities).reduce((a, b) => a + b, 0);
  const total = options?.products.reduce((sum, p) => sum + (quantities[p.id] ?? 0) * p.price_cents, 0) ?? 0;
  const valid = !!options && count > 0 && count <= options.max_qty &&
    options.products.every(p => (quantities[p.id] ?? 0) <= p.max_quantity);

  async function save() {
    if (!options || !valid || busy) return;
    setBusy(true);
    setError(null);
    try {
      const items = options.products.filter(p => quantities[p.id] > 0)
        .map(p => ({ product_id: p.id, quantity: quantities[p.id] }));
      const updated = await editBookedOrder(token, options.revision, items, note.trim(), total);
      setOpen(false);
      onSaved(updated);
    } catch (e) { setError(friendlyError(e)); }
    finally { setBusy(false); }
  }

  if (!open) return <Button variant="secondary" onClick={() => setOpen(true)}>Edit order</Button>;
  return <section className="ticket space-y-4 p-4" aria-label="Edit booked order">
    <h2 className="font-display text-xl font-extrabold">Edit your order</h2>
    <p className="text-sm text-muted">Add or remove menu items, change quantities and update your kitchen note. Your booking number and delivery location stay the same. Save before paying.</p>
    {error && <Notice tone="error">{error}</Notice>}
    {!options && !error && <p className="text-sm">Loading your booking…</p>}
    {options && <>
      <p className="text-sm font-bold">{count} / {options.max_qty} items</p>
      {options.products.map(p => {
        const q = quantities[p.id] ?? 0;
        return <div key={p.id} className="flex items-center justify-between gap-2 border-b border-line pb-3">
          <div className="min-w-0"><p className="font-bold">{p.name}</p><p className="text-sm text-muted">{formatRM(p.price_cents)} each</p></div>
          <div className="flex items-center gap-2">
            <button type="button" aria-label={"Remove one " + p.name} disabled={busy || q === 0}
              onClick={() => setQuantities(old => ({ ...old, [p.id]: Math.max(0, q - 1) }))}
              className="size-10 rounded-full border-2 border-ink font-bold disabled:opacity-40">−</button>
            <output className="w-6 text-center font-bold">{q}</output>
            <button type="button" aria-label={"Add one " + p.name} disabled={busy || q >= p.max_quantity || count >= options.max_qty}
              onClick={() => setQuantities(old => ({ ...old, [p.id]: q + 1 }))}
              className="size-10 rounded-full border-2 border-ink font-bold disabled:opacity-40">+</button>
          </div>
        </div>;
      })}
      <label className="block text-sm font-bold">Kitchen note (optional)
        <textarea className="mt-2 w-full rounded-xl border-2 border-ink bg-white p-3 font-normal" maxLength={120} value={note} disabled={busy} onChange={e => setNote(e.target.value)} />
      </label>
      <div className="flex justify-between font-bold"><span>New total</span><span>{formatRM(total)}</span></div>
      {count === 0 && <p className="text-sm text-muted">To remove the entire booking, use Cancel order instead.</p>}
      <Button onClick={() => void save()} busy={busy} disabled={!valid}>Save order changes</Button>
    </>}
    {error && <Button variant="secondary" disabled={busy} onClick={() => setAttempt(a => a + 1)}>Reload latest order</Button>}
    <Button variant="secondary" disabled={busy} onClick={() => setOpen(false)}>Discard changes</Button>
  </section>;
}
