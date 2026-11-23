import { supabase } from "./supabase";
import type { OrderTicket, OrderView, PaymentMethod, Storefront, TicketView } from "./types";

async function rpc<T>(fn: string, args?: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.rpc(fn, args);
  if (error) throw error;
  return data as T;
}

export const getStorefront = () => rpc<Storefront>("get_storefront");

export const createOrder = (a: {
  name: string; phone: string; buildingId: string; floor: number; room: string;
  items: { productId: string; quantity: number }[];
  method: PaymentMethod; idempotencyKey: string;
  tripId: string; note: string | null;
}) =>
  rpc<OrderTicket>("create_order_cart", {
    p_customer_name: a.name,
    p_phone: a.phone,
    p_building_id: a.buildingId,
    p_floor_number: a.floor,
    p_room_number: a.room,
    p_items: a.items.map((i) => ({ product_id: i.productId, quantity: i.quantity })),
    p_payment_method: a.method,
    p_idempotency_key: a.idempotencyKey,
    p_trip_id: a.tripId,
    p_note: a.note
  });

/** Customer self-cancel. Only works for unpaid orders that are not out for delivery. */
export const cancelOrder = (token: string) =>
  rpc<{ order_status: string }>("cancel_order_by_token", { p_token: token });

export const getOrder = (token: string) => rpc<OrderView>("get_order_by_token", { p_token: token });

/** Upload the receipt image to the private bucket, then attach it to the order. */
export async function uploadReceipt(token: string, file: Blob): Promise<void> {
  const path = `${token}/${Date.now()}.jpg`;
  const { error } = await supabase.storage
    .from("receipts")
    .upload(path, file, { contentType: "image/jpeg", upsert: false });
  if (error) throw error;
  await rpc("submit_receipt", { p_token: token, p_path: path });
}
/** Upload an optional ticket photo to the private bucket. Returns the storage path to attach to the ticket. */
export async function uploadTicketPhoto(file: Blob): Promise<string> {
  const path = `${crypto.randomUUID()}/${Date.now()}.jpg`;
  const { error } = await supabase.storage
    .from("ticket-attachments")
    .upload(path, file, { contentType: "image/jpeg", upsert: false });
  if (error) throw error;
  return path;
}

export const createTicket = (a: {
  idempotencyKey: string; orderToken: string | null; message: string;
  name: string; phone: string; attachmentPath: string | null;
}) =>
  rpc<TicketView>("create_support_ticket", {
    p_idempotency_key: a.idempotencyKey,
    p_order_token: a.orderToken,
    p_message: a.message,
    p_name: a.name,
    p_phone: a.phone,
    p_attachment_path: a.attachmentPath
  });

/** Status of tickets this device filed (the tokens are the only way to look them up). */
export const getTicketsByTokens = (tokens: string[]) =>
  rpc<TicketView[]>("get_support_tickets_by_tokens", { p_tokens: tokens });

