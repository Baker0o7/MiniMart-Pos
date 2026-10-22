// Supabase Edge Function: receives Safaricom Daraja C2B confirmations and files them for the right store.
// Deploy with JWT checking OFF (Safaricom cannot send a Supabase key):
//   supabase functions deploy till-payments --no-verify-jwt
// Register this address with Daraja (C2B "Register URL", both Confirmation and Validation):
//   https://<project-ref>.supabase.co/functions/v1/till-payments?t=<token shown in the app>
import { createClient } from "jsr:@supabase/supabase-js@2";

const accepted = () =>
  new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Accepted" }), {
    headers: { "Content-Type": "application/json" },
  });

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const token = new URL(req.url).searchParams.get("t") ?? "";
  if (token.length < 32) return accepted();            // say nothing useful to guessers
  let body: unknown;
  try { body = await req.json(); } catch { return accepted(); }

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { error } = await supabase.rpc("mpesa_ingest", { p_token: token, p_payload: body });
  if (error) console.error("mpesa_ingest failed", error.message);
  // Always answer "accepted": Safaricom retries (and may block the URL) if it sees errors.
  return accepted();
});
