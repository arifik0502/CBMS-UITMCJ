export type OrderStatus =
  | "NEW" | "CONFIRMED" | "PREPARING" | "READY" | "ASSIGNED"
  | "OUT_FOR_DELIVERY" | "DELIVERED" | "CANCELLED";

export type PaymentStatus =
  | "UNPAID" | "PENDING_VERIFICATION" | "VERIFIED" | "REJECTED"
  | "CASH_PENDING" | "CASH_COLLECTED" | "REFUND_PENDING" | "REFUNDED";

export type PaymentMethod = "CASH" | "DUITNOW";

export interface Building {
  id: string;
  code: string;
  name: string;
  floors: number[];
}

export interface Storefront {
  business_name: string;
  accepting_orders: boolean;
  max_qty_per_order: number;
  payment_expiry_minutes: number;
  duitnow_account_name: string;
  product: { id: string; name: string; description: string; price_cents: number } | null;
  available_stock: number;
  buildings: Building[];
}

export interface OrderTicket {
  order_no: string;
  tracking_token: string;
  total_cents: number;
  order_status: OrderStatus;
  payment_status: PaymentStatus;
  payment_method: PaymentMethod;
}

export interface OrderView {
  order_no: string;
  order_status: OrderStatus;
  payment_status: PaymentStatus;
  payment_method: PaymentMethod;
  total_cents: number;
  created_at: string;
  customer_name: string;
  building_code: string;
  floor_number: number;
  room_number: string;
  receipt_submitted: boolean;
  rejection_reason: string | null;
  cancelled_reason: string | null;
  payment_expires_at: string | null;
  items: { name: string; quantity: number; unit_price_cents: number; line_total_cents: number }[];
  timeline: { status: OrderStatus; at: string }[];
}
