import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { Button, ErrorScreen, Header, LinkButton, LoadingScreen, Notice, Pill, Shell } from "../components/ui";
import { formatRM, formatTime, roomLabel } from "../lib/format";
import { updateFromView } from "../lib/orderHistory";
import { ORDER_LABEL, PAYMENT_LABEL, STEPS, stepIndex } from "../lib/statusText";
import { useOrder } from "../lib/useOrder";

export default function TrackPage() {
  const { token } = useParams();
  const [params] = useSearchParams();
  const justPlaced = params.get("placed") === "1";
  const { order, error, loading, reload } = useOrder(token);
  const [copied, setCopied] = useState(false);

  // Keep "My orders" on this device up to date with what the customer just saw.
  useEffect(() => {
    if (token && order) updateFromView(token, order);
  }, [token, order]);

  if (loading && !order) return <Shell><Header back="/" /><LoadingScreen label="Finding your order" /></Shell>;
  if (error || !order) return <Shell><Header back="/" /><ErrorScreen message={error ?? "Order not found."} onRetry={reload} /></Shell>;

  const cancelled = order.order_status === "CANCELLED";
  const current = stepIndex(order.order_status);
  const pay = PAYMENT_LABEL[order.payment_status];
  const needsReceipt =
    order.payment_method === "DUITNOW" && !cancelled &&
    (order.payment_status === "UNPAID" || order.payment_status === "REJECTED");
  const trackUrl = `${window.location.origin}/track/${token}`;

  async function share() {
    try {
      if (navigator.share) {
        await navigator.share({ title: `Crispy Burger ${order!.order_no}`, url: trackUrl });
        return;
      }
      await navigator.clipboard.writeText(trackUrl);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2500);
    } catch { /* user cancelled share */ }
  }

  return (
    <Shell>
      <Header back="/" />

      {justPlaced && !cancelled && (
        <div className="mb-4">
          <Notice tone="success">
            Order placed. Keep this page or save the link below to follow your order.
          </Notice>
        </div>
      )}

      <article className="ticket">
        <div className="space-y-1 p-5">
          <p className="text-sm font-bold text-muted">Order</p>
          <h1 className="font-display text-3xl font-extrabold tracking-tight">{order.order_no}</h1>
          <div className="flex flex-wrap gap-2 pt-2">
            <Pill tone={cancelled ? "bad" : order.order_status === "DELIVERED" ? "good" : "neutral"}>
              {ORDER_LABEL[order.order_status]}
            </Pill>
            <Pill tone={pay.tone}>{pay.text}</Pill>
          </div>
        </div>
        <div className="tear" />
        <div className="space-y-2 p-5 text-sm">
          {order.items.map((i, idx) => (
            <div key={idx} className="flex justify-between gap-3">
              <span>{i.quantity} × {i.name}</span>
              <span className="font-semibold">{formatRM(i.line_total_cents)}</span>
            </div>
          ))}
          <div className="flex justify-between border-t-2 border-ink pt-2 text-base">
            <span className="font-bold">Total</span>
            <span className="font-display text-xl font-extrabold">{formatRM(order.total_cents)}</span>
          </div>
          {order.trip_label && (
            <div className="pt-1">
              <p className="font-semibold">{order.trip_label}: {order.trip_window}</p>
              <p className="text-sm text-muted">Your order is scheduled within this delivery window. Please keep your phone nearby.</p>
            </div>
          )}
          {order.note && (
            <p className="rounded-lg border-2 border-ink bg-bun/60 px-3 py-2 font-semibold">Custom: {order.note}</p>
          )}
          <p className="pt-1 text-muted">
            Deliver to {order.customer_name}, {roomLabel(order.building_code, order.room_number)} (floor {order.floor_number}).
            {" "}{order.payment_method === "CASH" ? "Paying cash on delivery." : "Paying by DuitNow QR."}
          </p>
        </div>
      </article>

      {needsReceipt && (
        <div className="mt-4 space-y-3">
          {order.payment_status === "REJECTED" && (
            <Notice tone="error">
              We could not accept your receipt{order.rejection_reason ? `: ${order.rejection_reason}` : "."} Upload a new one.
            </Notice>
          )}
          <LinkButton to={`/pay/${token}`}>
            {order.payment_status === "REJECTED" ? "Upload a new receipt" : "Pay with DuitNow QR"}
          </LinkButton>
        </div>
      )}

      {order.payment_status === "PENDING_VERIFICATION" && !cancelled && (
        <div className="mt-4">
          <Notice>We received your receipt. A team member is checking the payment. This is not confirmed until they approve it.</Notice>
        </div>
      )}

      {cancelled && (
        <div className="mt-4 space-y-3">
          <Notice tone="error">
            This order was cancelled{order.cancelled_reason ? `: ${order.cancelled_reason}` : "."}
            {order.payment_status === "REFUND_PENDING" && " Your refund is being arranged."}
          </Notice>
          <LinkButton to="/order">Place a new order</LinkButton>
        </div>
      )}

      {!cancelled && (
        <section className="ticket mt-5 p-5" aria-labelledby="progress">
          <h2 id="progress" className="font-display text-xl font-extrabold">Progress</h2>
          <ol className="mt-4 space-y-0">
            {STEPS.map((s, i) => {
              const done = i <= current;
              const isNow = i === current;
              const at = order.timeline.find((t) => t.status === s.status)?.at;
              return (
                <li key={s.status} className="flex gap-3" aria-current={isNow ? "step" : undefined}>
                  <div className="flex flex-col items-center">
                    <span className={`grid size-6 shrink-0 place-items-center rounded-full border-2 border-ink text-xs font-extrabold ${done ? "bg-ink text-bun" : "bg-white"} ${isNow ? "ring-4 ring-bun-deep" : ""}`}>
                      {done ? "✓" : ""}
                    </span>
                    {i < STEPS.length - 1 && <span className={`w-0.5 grow ${i < current ? "bg-ink" : "bg-line"}`} style={{ minHeight: 22 }} />}
                  </div>
                  <div className="pb-4">
                    <p className={`text-[0.95rem] ${done ? "font-bold" : "font-medium text-muted"}`}>{s.label}</p>
                    {at && done && <p className="text-xs text-muted">{formatTime(at)}</p>}
                  </div>
                </li>
              );
            })}
          </ol>
        </section>
      )}

      <div className="mt-5 space-y-3">
        <LinkButton to="/orders" variant="secondary">All my orders</LinkButton>
        <LinkButton to={`/support?order=${encodeURIComponent(token ?? "")}`} variant="secondary">Report a problem with this order</LinkButton>
        <Button variant="secondary" onClick={share}>{copied ? "Link copied" : "Save or share tracking link"}</Button>
        <p className="text-center text-xs text-muted">Anyone with this link can see this order. Keep it private.</p>
      </div>
    </Shell>
  );
}


