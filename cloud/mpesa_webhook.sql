-- MiniMart POS — instant M-Pesa payments (Daraja C2B confirmation webhook).
-- Run AFTER supabase_setup.sql, once, in the Supabase SQL editor. Safe to run again.
--
-- How it works
--   1. Safaricom sends every payment to your till/paybill to a public HTTPS address (the Edge Function in
--      cloud/functions/till-payments). The address contains a long random token that belongs to ONE store.
--   2. The function hands the payment to mpesa_ingest(), which files it in mpesa_inbox for that store.
--   3. The store's app asks for its waiting payments (claim_mpesa_payments), records them, and confirms
--      (ack_mpesa_payments). Nothing can be read with just the public anon key.
--
-- Note: Safaricom refuses callback addresses containing words such as "mpesa", "safaricom", "sql" or "exe".
-- The function is therefore called "till-payments".

create extension if not exists pgcrypto with schema extensions;

create table if not exists mpesa_routes (
  store_id   uuid primary key references stores(id) on delete cascade,
  token_hash text not null unique,
  created_at timestamptz not null default now()
);

create table if not exists mpesa_inbox (
  id           bigserial primary key,
  store_id     uuid not null references stores(id) on delete cascade,
  trans_id     text not null,
  amount       numeric(14,2) not null,
  sender_name  text not null default '',
  sender_phone text not null default '',
  paid_at      timestamptz not null,
  received_at  timestamptz not null default now(),
  delivered_at timestamptz,
  unique (store_id, trans_id)
);
create index if not exists mpesa_inbox_waiting on mpesa_inbox (store_id) where delivered_at is null;

alter table mpesa_routes enable row level security;
alter table mpesa_inbox  enable row level security;
revoke all on mpesa_routes, mpesa_inbox from anon, authenticated;

-- A registered store turns the webhook on (or replaces its token). The token is returned once; only a hash is kept.
create or replace function enable_mpesa_webhook(p_store_id uuid, p_secret text)
returns text language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype; tok text;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    perform pg_sleep(1.5);
    raise exception 'Unknown store or wrong secret';
  end if;
  tok := encode(gen_random_bytes(24), 'hex');
  insert into mpesa_routes (store_id, token_hash) values (p_store_id, encode(digest(tok, 'sha256'), 'hex'))
    on conflict (store_id) do update set token_hash = excluded.token_hash, created_at = now();
  return tok;
end $$;

-- Called only by the Edge Function (service role). Accepts a Daraja C2B confirmation body for the store owning the token.
create or replace function mpesa_ingest(p_token text, p_payload jsonb)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare sid uuid; tid text; amt numeric; nm text; ph text; ts timestamptz;
begin
  if length(coalesce(p_token, '')) < 32 or pg_column_size(p_payload) > 20000 then return; end if;
  select store_id into sid from mpesa_routes where token_hash = encode(digest(p_token, 'sha256'), 'hex');
  if sid is null then return; end if;

  tid := upper(trim(coalesce(p_payload->>'TransID', '')));
  if tid !~ '^[A-Z0-9]{8,12}$' then return; end if;
  begin amt := (p_payload->>'TransAmount')::numeric; exception when others then return; end;
  if amt is null or amt <= 0 or amt > 1000000 then return; end if;

  nm := left(trim(concat_ws(' ', nullif(p_payload->>'FirstName',''), nullif(p_payload->>'MiddleName',''), nullif(p_payload->>'LastName',''))), 60);
  ph := coalesce(p_payload->>'MSISDN', '');
  if ph ~ '^[0-9a-fA-F]{64}$' then ph := ''; end if;   -- Safaricom now sends a hash instead of the number
  ph := left(ph, 20);
  begin
    ts := to_timestamp(p_payload->>'TransTime', 'YYYYMMDDHH24MISS') at time zone 'Africa/Nairobi';
  exception when others then ts := now(); end;

  insert into mpesa_inbox (store_id, trans_id, amount, sender_name, sender_phone, paid_at)
  values (sid, tid, amt, coalesce(nm, ''), ph, coalesce(ts, now()))
  on conflict (store_id, trans_id) do nothing;
end $$;

-- The store app fetches its waiting payments (oldest first, at most 200 per call).
create or replace function claim_mpesa_payments(p_store_id uuid, p_secret text)
returns jsonb language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    perform pg_sleep(1.5);
    raise exception 'Unknown store or wrong secret';
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', id, 'code', trans_id, 'amount', amount, 'name', sender_name, 'phone', sender_phone,
             'paidAtMs', (extract(epoch from paid_at) * 1000)::bigint) order by id)
    from (select * from mpesa_inbox where store_id = p_store_id and delivered_at is null order by id limit 200) w
  ), '[]'::jsonb);
end $$;

-- The app confirms what it has safely recorded; confirmed rows older than 30 days are tidied away.
create or replace function ack_mpesa_payments(p_store_id uuid, p_secret text, p_ids bigint[])
returns void language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    perform pg_sleep(1.5);
    raise exception 'Unknown store or wrong secret';
  end if;
  update mpesa_inbox set delivered_at = now() where store_id = p_store_id and id = any(p_ids) and delivered_at is null;
  delete from mpesa_inbox where store_id = p_store_id and delivered_at < now() - interval '30 days';
end $$;

revoke all on function enable_mpesa_webhook(uuid, text) from public;
revoke all on function mpesa_ingest(text, jsonb) from public, anon, authenticated;
revoke all on function claim_mpesa_payments(uuid, text) from public;
revoke all on function ack_mpesa_payments(uuid, text, bigint[]) from public;
grant execute on function enable_mpesa_webhook(uuid, text) to anon, authenticated;
grant execute on function claim_mpesa_payments(uuid, text) to anon, authenticated;
grant execute on function ack_mpesa_payments(uuid, text, bigint[]) to anon, authenticated;
grant execute on function mpesa_ingest(text, jsonb) to service_role;
