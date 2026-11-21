import { useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Button, Field, Header, inputClass, LinkButton, Notice, Shell } from "../components/ui";
import { createTicket, uploadTicketPhoto } from "../lib/api";
import { friendlyError } from "../lib/errors";
import { compressImage } from "../lib/image";
import { readHistory } from "../lib/orderHistory";
import { safeSessionGet, safeSessionRemove, safeSessionSet } from "../lib/storage";
import { readContact, rememberTicket, saveContact } from "../lib/ticketHistory";
import type { TicketView } from "../lib/types";
import {
  normalizePhone, TICKET_MESSAGE_MAX, validateTicket, type TicketFormErrors
} from "../lib/validation";

const TICKET_KEY = "cbms:ticketIdempotencyKey";
const GENERAL = "";

function ticketKey(): string {
  let key = safeSessionGet(TICKET_KEY);
  if (!key) {
    key = crypto.randomUUID();
    safeSessionSet(TICKET_KEY, key);
  }
  return key;
}

export default function SupportPage() {
  const [params] = useSearchParams();
  const requested = params.get("order") ?? GENERAL;

  // Orders this device placed; a ticket can be about one of them or about anything else.
  const orders = useMemo(() => readHistory().filter((o) => o.orderNo && !o.unavailable), []);
  const known = orders.some((o) => o.token === requested);

  const contact = useMemo(() => readContact(), []);
  const [orderToken, setOrderToken] = useState(requested);
  const [message, setMessage] = useState("");
  const [name, setName] = useState(contact.name);
  const [phone, setPhone] = useState(contact.phone);
  const [photo, setPhoto] = useState<File | null>(null);
  const [errors, setErrors] = useState<TicketFormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const [sent, setSent] = useState<TicketView | null>(null);
  const uploaded = useRef<{ file: File; path: string } | null>(null);

  const previewUrl = useMemo(() => (photo ? URL.createObjectURL(photo) : null), [photo]);
  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);

  function onPick(ev: ChangeEvent<HTMLInputElement>) {
    const file = ev.target.files?.[0] ?? null;
    ev.target.value = "";
    setPhotoError(null);
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      setPhotoError("Choose a JPG, PNG or WebP image.");
      return;
    }
    uploaded.current = null;
    setPhoto(file);
  }

  async function onSubmit(ev: FormEvent) {
    ev.preventDefault();
    if (submitting) return;
    const found = validateTicket({ message, name, phone });
    setErrors(found);
    if (Object.keys(found).length > 0) {
      document.getElementById(Object.keys(found)[0])?.focus();
      return;
    }
    setSubmitting(true);
    setSubmitError(null);
    try {
      let path: string | null = null;
      if (photo) {
        // Keep the uploaded path so a retry after a failed submit does not upload the photo twice.
        if (uploaded.current?.file !== photo) {
          uploaded.current = { file: photo, path: await uploadTicketPhoto(await compressImage(photo)) };
        }
        path = uploaded.current.path;
      }
      const ticket = await createTicket({
        idempotencyKey: ticketKey(),
        orderToken: orderToken || null,
        message: message.trim(),
        name: name.trim(),
        phone: normalizePhone(phone),
        attachmentPath: path
      });
      safeSessionRemove(TICKET_KEY);
      saveContact({ name, phone });
      rememberTicket(ticket, orderToken || null);
      setSent(ticket);
    } catch (e) {
      setSubmitError(
        e instanceof Error && !("hint" in e) && /image/i.test(e.message) ? e.message : friendlyError(e)
      );
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <Shell>
        <Header back="/orders" />
        <div className="ticket space-y-4 p-6 text-center" role="status">
          <p className="font-display text-3xl font-extrabold">Report sent</p>
          <p className="text-sm font-medium">
            Your ticket number is <strong>{sent.ticket_no}</strong>. Our team will look into it and may call you on the number you gave.
          </p>
          <p className="text-sm text-muted">You can follow its status under My orders.</p>
          <LinkButton to="/orders">View my orders and tickets</LinkButton>
          <LinkButton to="/" variant="secondary">Back to menu</LinkButton>
        </div>
      </Shell>
    );
  }

  const aria = (k: keyof TicketFormErrors) => ({
    "aria-invalid": errors[k] ? true : undefined,
    "aria-describedby": errors[k] ? `${k}-error` : undefined
  });

  return (
    <Shell>
      <Header back="/orders" />
      <h1 className="font-display text-3xl font-extrabold tracking-tight">Report a problem</h1>
      <p className="mt-1 text-sm text-muted">Something wrong with an order, or a question for the team? Tell us and we will follow up.</p>

      <form onSubmit={onSubmit} noValidate className="mt-4 space-y-5">
        <section className="ticket space-y-4 p-5">
          <Field label="What is this about?" htmlFor="order">
            <select id="order" className={inputClass} value={orderToken} onChange={(e) => setOrderToken(e.target.value)}>
              <option value={GENERAL}>Not about a specific order</option>
              {requested && !known && <option value={requested}>This order</option>}
              {orders.map((o) => <option key={o.token} value={o.token}>Order {o.orderNo}</option>)}
            </select>
          </Field>

          <Field
            label="What went wrong?"
            htmlFor="message"
            error={errors.message}
            hint={`${message.trim().length}/${TICKET_MESSAGE_MAX}. e.g. the burger was cold, an item was missing.`}
          >
            <textarea id="message" rows={5} maxLength={TICKET_MESSAGE_MAX} className={inputClass} value={message}
              onChange={(e) => { setMessage(e.target.value); setErrors((v) => ({ ...v, message: undefined })); setSubmitError(null); }}
              {...aria("message")} />
          </Field>

          <div>
            <p className="mb-1.5 text-sm font-bold">Photo (optional)</p>
            {photo && previewUrl ? (
              <div className="flex items-center gap-3 rounded-xl border-2 border-ink bg-white p-3">
                <img src={previewUrl} alt="Selected photo" className="size-16 rounded-lg border-2 border-ink object-cover" />
                <p className="min-w-0 flex-1 truncate text-sm font-semibold">{photo.name}</p>
                <button type="button" onClick={() => { setPhoto(null); uploaded.current = null; }}
                  className="min-h-10 rounded-lg px-3 text-sm font-bold text-ketchup underline underline-offset-2">Remove</button>
              </div>
            ) : (
              <label htmlFor="photo"
                className="flex min-h-12 cursor-pointer items-center justify-center rounded-xl border-2 border-dashed border-ink px-4 py-3 text-sm font-bold has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-ketchup">
                Add a photo
                <input id="photo" type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" onChange={onPick} />
              </label>
            )}
            {photoError && <p role="alert" className="mt-1 text-sm font-semibold text-ketchup">{photoError}</p>}
          </div>
        </section>

        <section className="ticket space-y-4 p-5">
          <h2 className="font-display text-xl font-extrabold">How can we reach you?</h2>
          <Field label="Your name" htmlFor="name" error={errors.name}>
            <input id="name" className={inputClass} autoComplete="name" value={name} maxLength={80}
              onChange={(e) => { setName(e.target.value); setErrors((v) => ({ ...v, name: undefined })); }} {...aria("name")} />
          </Field>
          <Field label="Phone number" htmlFor="phone" error={errors.phone} hint="We may call or message you about this.">
            <input id="phone" className={inputClass} type="tel" inputMode="tel" autoComplete="tel" placeholder="012-345 6789"
              value={phone} onChange={(e) => { setPhone(e.target.value); setErrors((v) => ({ ...v, phone: undefined })); }} {...aria("phone")} />
          </Field>
        </section>

        {submitError && <Notice tone="error">{submitError}</Notice>}
        <Button type="submit" busy={submitting}>{submitting ? "Sending" : "Send report"}</Button>
      </form>
    </Shell>
  );
}
