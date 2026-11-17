import { supabase } from "./supabase";
import type { OrderTicket, OrderView, PaymentMethod, Storefront } from "./types";

async function rpc<T>(fn: string, args?: Record<string, unknown>): Promise<T> {
  const { data, error } = await supabase.rpc(fn, args);
  if (error) throw error;
  return data as T;
}

export const getStorefront = () => rpc<Storefront>("get_storefront");

export const createOrder = (a: {
  name: string; phone: string; buildingId: string; floor: number; room: string;
  quantity: number; method: PaymentMethod; idempotencyKey: string;
  tripId: string; note: string | null;
}) =>
  rpc<OrderTicket>("create_order", {
    p_customer_name: a.name,
    p_phone: a.phone,
    p_building_id: a.buildingId,
    p_floor_number: a.floor,
    p_room_number: a.room,
    p_quantity: a.quantity,
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
