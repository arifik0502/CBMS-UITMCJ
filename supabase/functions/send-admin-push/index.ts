const url = Deno.env.get("SUPABASE_URL") ?? "";
const serviceRole = Deno.env.get("CBMS_SUPABASE_SERVICE_ROLE_KEY") ??
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const firebaseJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON") ?? "";
const webhookSecret = Deno.env.get("CBMS_WEBHOOK_SECRET") ?? "";
const developerEmail = (Deno.env.get("CBMS_OWNER_EMAIL") ?? "").trim().toLowerCase();

type Obj = Record<string, any>;
type Event = { type: "order" | "payment" | "inventory" | "ticket"; id: string; title: string; body: string; ticket_id?: string };

let accessToken = "";
let accessTokenExpiry = 0;

function response(body: Obj, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" }
  });
}

function equalSecret(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let result = 0;
  for (let i = 0; i < a.length; i++) result |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return result === 0;
}

function b64url(value: Uint8Array | string): string {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/g, "");
}

function text(value: unknown, max = 120): string {
  return String(value ?? "").replace(/[\r\n\t]/g, " ").trim().slice(0, max);
}

async function firebaseBearer(): Promise<{ token: string; projectId: string }> {
  if (accessToken && Date.now() < accessTokenExpiry - 60_000) {
    const account = JSON.parse(firebaseJson);
    return { token: accessToken, projectId: account.project_id };
  }

  const account = JSON.parse(firebaseJson);
  if (!account.project_id || !account.client_email || !account.private_key) {
    throw new Error("Firebase service account is incomplete.");
  }

  const pem = String(account.private_key)
    .replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const keyBytes = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64url(JSON.stringify({
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600
    }));
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned)
  ));
  const assertion = unsigned + "." + b64url(signature);

  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  if (!tokenResponse.ok) throw new Error("Firebase OAuth exchange failed.");
  const tokenData = await tokenResponse.json();
  accessToken = String(tokenData.access_token ?? "");
  accessTokenExpiry = Date.now() + Number(tokenData.expires_in ?? 3600) * 1000;
  if (!accessToken) throw new Error("Firebase returned no access token.");
  return { token: accessToken, projectId: account.project_id };
}

async function rest(path: string): Promise<Obj[]> {
  const res = await fetch(url + "/rest/v1/" + path, {
    headers: { apikey: serviceRole, Authorization: "Bearer " + serviceRole }
  });
  if (!res.ok) throw new Error("Supabase REST lookup failed.");
  return await res.json();
}

async function developerTokens(): Promise<string[]> {
  const authRes = await fetch(url + "/auth/v1/admin/users?page=1&per_page=1000", {
    headers: { apikey: serviceRole, Authorization: "Bearer " + serviceRole }
  });
  if (!authRes.ok) throw new Error("Developer account lookup failed.");
  const authData = await authRes.json();
  const users = Array.isArray(authData) ? authData : authData.users ?? [];
  const owner = users.find((user: Obj) =>
    String(user.email ?? "").toLowerCase() === developerEmail
  );
  if (!owner?.id) return [];

  const profileQuery = new URLSearchParams({
    select: "id",
    id: "eq." + String(owner.id),
    role: "eq.OWNER",
    is_active: "eq.true",
    limit: "1"
  });
  const activeOwner = await rest("profiles?" + profileQuery.toString());
  if (activeOwner.length !== 1) return [];

  const query = new URLSearchParams({
    select: "token",
    user_id: "eq." + owner.id,
    platform: "eq.android"
  });
  const rows = await rest("device_tokens?" + query.toString());
  return rows.map((row) => String(row.token ?? "")).filter(Boolean);
}

async function send(token: string, event: Event, bearer: string, projectId: string): Promise<boolean> {
  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: { Authorization: "Bearer " + bearer, "content-type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: {
            type: event.type,
            event_id: event.id,
            title: event.title,
            body: event.body,
            ...(event.ticket_id ? { ticket_id: event.ticket_id } : {})
          },
          android: { priority: "HIGH" }
        }
      })
    }
  );

  if (res.ok) return true;
  const details = await res.json().catch(() => ({}));
  if (details?.error?.details?.some((item: Obj) => item.errorCode === "UNREGISTERED")) {
    const query = new URLSearchParams({ token: "eq." + token });
    await fetch(url + "/rest/v1/device_tokens?" + query.toString(), {
      method: "DELETE",
      headers: {
        apikey: serviceRole,
        Authorization: "Bearer " + serviceRole,
        Prefer: "return=minimal"
      }
    });
  }
  return false;
}

function formatTripTime(value: unknown): string {
  const match = /^(\d{2}):(\d{2})/.exec(text(value, 16));
  if (!match) return "";
  const hour = Number(match[1]);
  const minute = match[2];
  const displayHour = hour % 12 || 12;
  return `${displayHour}:${minute} ${hour < 12 ? "AM" : "PM"}`;
}

async function buildEvents(payload: Obj): Promise<Event[]> {
  const table = String(payload.table ?? "");
  const action = String(payload.type ?? "").toUpperCase();
  const row = payload.record ?? {};
  const old = payload.old_record ?? {};
  const id = text(row.id, 80);
  const orderNo = text(row.order_no, 40);
  if (table === "support_tickets" && action === "INSERT" && id) {
    const ticketNo = text(row.ticket_no, 40) || "New support ticket";
    const name = text(row.customer_name, 60);
    const preview = text(String(row.message ?? "").split(/\r?\n/, 1)[0], 100);
    return [{
      type: "ticket",
      id: "ticket:" + id,
      ticket_id: id,
      title: "New support ticket",
      body: [ticketNo, name ? "from " + name : "", preview].filter(Boolean).join(" · ").slice(0, 240)
    }];
  }

  if (table === "orders" && action === "INSERT" && id) {
    let tripText = "";
    const tripId = text(row.trip_id, 80);
    if (tripId) {
      try {
        const query = new URLSearchParams({
          select: "label,start_time,end_time",
          id: "eq." + tripId,
          limit: "1"
        });
        const trip = (await rest("delivery_trips?" + query.toString()))[0];
        if (trip) {
          const label = text(trip.label, 40) || "Delivery trip";
          const start = formatTripTime(trip.start_time);
          const end = formatTripTime(trip.end_time);
          tripText = start && end ? ` — ${label} (${start}–${end})` : ` — ${label}`;
        }
      } catch {
        tripText = "";
      }
    }
    return [{
      type: "order",
      id: "order:" + id + ":created",
      title: "New CBMS order",
      body: (orderNo ? "Order " + orderNo + " has been submitted." : "A new order has been submitted.") + tripText
    }];
  }

  if (table === "orders" && action === "UPDATE" && id) {
    const result: Event[] = [];
    if (row.payment_status !== old.payment_status) {
      const status = String(row.payment_status ?? "");
      const messages: Record<string, [string, string]> = {
        PENDING_VERIFICATION: ["Payment receipt submitted", orderNo ? "A receipt for order " + orderNo + " is ready for review." : "A receipt is ready for review."],
        VERIFIED: ["Payment verified", orderNo ? "Payment for order " + orderNo + " has been verified." : "A payment has been verified."],
        REJECTED: ["Payment rejected", orderNo ? "Payment for order " + orderNo + " was rejected." : "A payment was rejected."],
        CASH_COLLECTED: ["Cash payment collected", orderNo ? "Cash for order " + orderNo + " has been collected." : "Cash has been collected."]
      };
      if (messages[status]) {
        result.push({
          type: "payment",
          id: "payment:" + id + ":" + text(old.payment_status, 40) + "->" + status,
          title: messages[status][0],
          body: messages[status][1]
        });
      }
    }
    if (row.order_status !== old.order_status) {
      const status = text(row.order_status, 40);
      result.push({
        type: "order",
        id: "order:" + id + ":status:" + text(old.order_status, 40) + "->" + status,
        title: "Order status updated",
        body: orderNo ? "Order " + orderNo + " is now " + status.toLowerCase().replaceAll("_", " ") + "." : "An order status has changed."
      });
    }
    return result;
  }

  if (table === "inventory" && action === "UPDATE" && row.physical_stock !== old.physical_stock) {
    return [{
      type: "inventory",
      id: "inventory:" + text(row.product_id, 80) + ":physical:" + text(row.physical_stock, 20),
      title: "Inventory updated",
      body: "Physical stock changed to " + text(row.physical_stock, 20) + "."
    }];
  }
  return [];
}

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return response({ error: "Method not allowed." }, 405);
  if (!url || !serviceRole || !firebaseJson || !webhookSecret) {
    return response({ error: "Push service is not configured." }, 503);
  }
  if (!equalSecret(request.headers.get("x-cbms-webhook-secret") ?? "", webhookSecret)) {
    return response({ error: "Unauthorized." }, 401);
  }

  try {
    const payload = await request.json();
    if (String(payload.schema ?? "") !== "public") return response({ ignored: true });
    const events = await buildEvents(payload);
    if (!events.length) return response({ sent: 0, ignored: true });

    const tokens = await developerTokens();
    if (!tokens.length) return response({ sent: 0, registeredDevices: 0 });

    const firebase = await firebaseBearer();
    let sent = 0;
    let failed = 0;
    for (const event of events) {
      for (const token of tokens) {
        if (await send(token, event, firebase.token, firebase.projectId)) sent++;
        else failed++;
      }
    }
    return response({ sent, failed, eventCount: events.length, registeredDevices: tokens.length });
  } catch {
    return response({ error: "Push notification processing failed." }, 500);
  }
});
