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

export interface Trip {
  id: string;
  trip_no: number;
  label: string;
  start_time: string;
  end_time: string;
  start_label: string;
  end_label: string;
  closes_label: string;
  closed_reason: "TEAM" | "CUTOFF" | "ENDED" | null;
}

export interface StorefrontProduct {
  id: string;
  name: string;
  description: string;
  price_cents: number;
  image_url: string | null;
  available_stock: number;
}

export interface Storefront {
  business_name: string;
  accepting_orders: boolean;
  max_qty_per_order: number;
  payment_expiry_minutes: number;
  duitnow_account_name: string;
  products: StorefrontProduct[];
  /** Legacy first product fields kept while older deployments transition. */
  product: StorefrontProduct | null;
  available_stock: number;
  buildings: Building[];
  trips: Trip[];
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
  trip_label: string | null;
  trip_window: string | null;
  note: string | null;
  receipt_submitted: boolean;
  rejection_reason: string | null;
  cancelled_reason: string | null;
  payment_expires_at: string | null;
  items: { name: string; quantity: number; unit_price_cents: number; line_total_cents: number }[];
  timeline: { status: OrderStatus; at: string }[];
}
export type TicketStatus = "OPEN" | "RESOLVED";

/** What a customer may see about their own support ticket. */
export interface TicketView {
  ticket_no: string;
  token: string;
  status: TicketStatus;
  message: string;
  created_at: string;
  resolved_at: string | null;
  order_no: string | null;
  has_photo: boolean;
}

