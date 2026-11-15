import { useEffect, useMemo, useState, type ChangeEvent } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { Button, ErrorScreen, Header, LinkButton, LoadingScreen, Notice, Shell } from "../components/ui";
import { uploadReceipt } from "../lib/api";
import { friendlyError } from "../lib/errors";
import { formatRM } from "../lib/format";
import { compressImage } from "../lib/image";
import { useOrder } from "../lib/useOrder";
import { useStorefront } from "../lib/useStorefront";

const QR_URL = (import.meta.env.VITE_DUITNOW_QR_URL as string | undefined) || "/duitnow-qr.svg";
const MAX_INPUT_BYTES = 15 * 1024 * 1024;

function useCountdown(iso: string | null) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!iso) return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [iso]);
  if (!iso) return null;
  return Math.max(0, Math.floor((new Date(iso).getTime() - now) / 1000));
}

export default function PayPage() {
  const { token } = useParams();
  const navigate = useNavigate();
  const { order, error, loading, reload } = useOrder(token, 10000);
  const { data: store } = useStorefront();
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const remaining = useCountdown(order?.payment_expires_at ?? null);

  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  if (loading && !order) return <Shell><Header /><LoadingScreen label="Loading payment" /></Shell>;
  if (error || !order) return <Shell><Header back="/" /><ErrorScreen message={error ?? "Order not found."} onRetry={reload} /></Shell>;

  const canPay =
    order.payment_method === "DUITNOW" && order.order_status !== "CANCELLED" &&
    (order.payment_status === "UNPAID" || order.payment_status === "REJECTED");
  if (!canPay) return <Navigate to={`/track/${token}`} replace />;

  const expired = remaining !== null && remaining <= 0;
  const mm = remaining !== null ? String(Math.floor(remaining / 60)).padStart(2, "0") : "";
  const ss = remaining !== null ? String(remaining % 60).padStart(2, "0") : "";

  function onPick(e: ChangeEvent<HTMLInputElement>) {
    setUploadError(null);
    const f = e.target.files?.[0] ?? null;
    if (f && f.size > MAX_INPUT_BYTES) {
      setUploadError("That image is too large. Choose a smaller screenshot.");
      setFile(null);
      return;
    }
    setFile(f);
  }

  async function submit() {
    if (!file || !token || busy) return;
    setBusy(true);
    setUploadError(null);
    try {
      const blob = await compressImage(file);
      await uploadReceipt(token, blob);
      navigate(`/track/${token}`, { replace: true });
    } catch (e) {
      setUploadError(e instanceof Error && !("hint" in e) && /image/i.test(e.message) ? e.message : friendlyError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Shell>
      <Header />
      <h1 className="font-display text-3xl font-extrabold tracking-tight">Pay with DuitNow</h1>
      <p className="mt-1 text-sm font-medium">Order {order.order_no}</p>

      {order.payment_status === "REJECTED" && (
        <div className="mt-4">
          <Notice tone="error">
            Your last receipt was not accepted{order.rejection_reason ? `: ${order.rejection_reason}` : "."} Pay the exact amount and upload a new receipt.
          </Notice>
        </div>
      )}

      <section className="ticket mt-4">
        <div className="p-5 text-center">
          <p className="text-sm font-bold text-muted">Amount to pay</p>
          <p className="font-display text-5xl font-extrabold tracking-tight">{formatRM(order.total_cents)}</p>
          {store?.duitnow_account_name && (
            <p className="mt-1 text-sm font-semibold">Pay to {store.duitnow_account_name}</p>
          )}
        </div>
        <div className="tear" />
        <div className="grid place-items-center p-5">
          <img src={QR_URL} alt="DuitNow QR code" className="aspect-square w-full max-w-64 rounded-lg border-2 border-ink bg-white" />
          <p className="mt-3 text-center text-sm text-muted">
            Open your banking or e-wallet app, scan this QR, and pay the exact amount.
          </p>
        </div>
      </section>

      {remaining !== null && (
        <div className="mt-4">
          {expired ? (
            <Notice tone="error">
              Time is up. If you have not paid, your order may be cancelled. If you already paid, upload your receipt now.
            </Notice>
          ) : (
            <Notice>
              Upload your receipt within <strong>{mm}:{ss}</strong> to keep your burgers reserved.
            </Notice>
          )}
        </div>
      )}

      <section className="ticket mt-5 space-y-4 p-5">
        <h2 className="font-display text-xl font-extrabold">Upload your receipt</h2>
        <p className="text-sm text-muted">
          Screenshot the payment success screen. A team member checks it against our account before approving. Uploading does not confirm payment.
        </p>

        <label htmlFor="receipt"
          className="flex min-h-14 cursor-pointer items-center justify-center rounded-xl border-2 border-dashed border-ink bg-white px-4 py-3 text-center font-bold has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ketchup">
          {file ? "Choose a different image" : "Choose screenshot"}
          <input id="receipt" type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={onPick} />
        </label>

        {preview && (
          <img src={preview} alt="Your receipt preview" className="mx-auto max-h-72 rounded-lg border-2 border-ink object-contain" />
        )}

        {uploadError && <Notice tone="error">{uploadError}</Notice>}

        <Button onClick={submit} busy={busy} disabled={!file}>
          {busy ? "Uploading" : "Submit receipt"}
        </Button>
      </section>

      <div className="mt-4">
        <LinkButton to={`/track/${token}`} variant="secondary">I will upload later</LinkButton>
      </div>
    </Shell>
  );
}
