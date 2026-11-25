import { useState } from "react";
import { cancelOrder } from "../lib/api";
import { friendlyError } from "../lib/errors";
import { Notice, Spinner } from "./ui";

/**
 * "Cancel order" with a quick yes/no check. It never asks for a reason.
 * `compact` renders a small inline button (used on the My Orders cards).
 */
export default function CancelOrder({
  token, onCancelled, compact = false
}: { token: string; onCancelled: () => void; compact?: boolean }) {
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function doCancel() {
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      await cancelOrder(token);
      onCancelled();
    } catch (e) {
      setError(friendlyError(e));
      setBusy(false);
    }
  }

  if (!confirming) {
    return (
      <button
        type="button"
        onClick={() => setConfirming(true)}
        className={
          compact
            ? "min-h-10 rounded-lg border-2 border-ketchup bg-paper px-4 py-2 text-sm font-bold text-ketchup active:translate-y-0.5"
            : "min-h-12 w-full rounded-xl border-2 border-ketchup bg-paper px-5 py-3 text-base font-bold text-ketchup active:translate-y-0.5"
        }
      >
        Delete / cancel order
      </button>
    );
  }

  return (
    <div className="w-full space-y-3 rounded-xl border-2 border-ketchup bg-paper p-4">
      <p className="font-bold">Cancel this order?</p>
      <p className="text-sm text-muted">Your booking will be cancelled and reserved stock released. A cancelled record is kept for your order history.</p>
      {error && <Notice tone="error">{error}</Notice>}
      <div className="grid grid-cols-2 gap-3">
        <button
          type="button"
          onClick={doCancel}
          disabled={busy}
          className="inline-flex min-h-12 items-center justify-center gap-2 rounded-xl border-2 border-ink bg-ketchup px-3 py-3 font-bold text-white disabled:opacity-60"
        >
          {busy && <Spinner />}
          Yes, cancel
        </button>
        <button
          type="button"
          onClick={() => { setConfirming(false); setError(null); }}
          disabled={busy}
          className="min-h-12 rounded-xl border-2 border-ink bg-white px-3 py-3 font-bold disabled:opacity-60"
        >
          Keep order
        </button>
      </div>
    </div>
  );
}
