import { createClient } from "@supabase/supabase-js";

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined;
const anon = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined;

export const isConfigured = Boolean(url && anon);

// Public anon key only. All customer access goes through RPC functions; see supabase/migrations.
export const supabase = createClient(url ?? "https://example.invalid", anon ?? "missing", {
  auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false }
});
