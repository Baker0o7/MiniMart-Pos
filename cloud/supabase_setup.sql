-- MiniMart POS — multi-store cloud dashboard: Supabase setup.
-- Run this once in the Supabase SQL editor (Project → SQL Editor → New query → Run).
--
-- How it works
--   * Each store's app registers itself once with a setup code only you know, receiving no
--     special rights beyond writing ITS OWN snapshot (it proves who it is with a random secret
--     that only a hash of is stored here).
--   * The dashboard signs in as you (Supabase Auth) and can read every store's latest snapshot.
--   * Nobody holding just the public "anon" key can read anything.

create extension if not exists pgcrypto with schema extensions;

-- 1. CHANGE THIS before running: the code you type into each store's app the first time.
create table if not exists app_config (key text primary key, value text not null);
insert into app_config (key, value) values ('setup_code', 'CHANGE-ME-TO-A-LONG-RANDOM-CODE')
on conflict (key) do nothing;

create table if not exists stores (
  id          uuid primary key,
  name        text not null,
  secret_hash text not null,
  created_at  timestamptz not null default now()
);

create table if not exists store_snapshots (
  store_id   uuid primary key references stores(id) on delete cascade,
  store_name text not null,
  payload    jsonb not null,
  pushed_at  timestamptz not null default now()
);

-- Row-level security: stores / app_config have NO policies, so no API role can read them directly.
alter table app_config      enable row level security;
alter table stores          enable row level security;
alter table store_snapshots enable row level security;

drop policy if exists "signed-in owner reads snapshots" on store_snapshots;
create policy "signed-in owner reads snapshots" on store_snapshots
  for select to authenticated using (true);

revoke all on app_config, stores, store_snapshots from anon, authenticated;
grant select on store_snapshots to authenticated;

-- 2. A store app registers itself (once).
create or replace function register_store(p_setup_code text, p_store_id uuid, p_name text, p_secret text)
returns void language plpgsql security definer set search_path = public, extensions as $$
begin
  if p_setup_code is distinct from (select value from app_config where key = 'setup_code') then
    raise exception 'Invalid setup code';
  end if;
  if length(coalesce(p_secret, '')) < 24 then
    raise exception 'Store secret too short';
  end if;
  if exists (select 1 from stores where id = p_store_id) then
    raise exception 'This store is already registered';
  end if;
  insert into stores (id, name, secret_hash) values (p_store_id, left(p_name, 80), crypt(p_secret, gen_salt('bf')));
end $$;

-- 3. A registered store uploads its latest snapshot.
create or replace function push_snapshot(p_store_id uuid, p_secret text, p_payload jsonb)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    raise exception 'Unknown store or wrong secret';
  end if;
  if pg_column_size(p_payload) > 400000 then
    raise exception 'Snapshot too large';
  end if;
  -- keep the store's name in step with the app (the owner may rename the shop)
  if coalesce(p_payload->>'storeName', '') <> '' and left(p_payload->>'storeName', 80) <> s.name then
    s.name := left(p_payload->>'storeName', 80);
    update stores set name = s.name where id = p_store_id;
  end if;
  insert into store_snapshots (store_id, store_name, payload, pushed_at)
  values (p_store_id, s.name, p_payload, now())
  on conflict (store_id) do update
    set store_name = excluded.store_name, payload = excluded.payload, pushed_at = now();
end $$;

revoke all on function register_store(text, uuid, text, text) from public;
revoke all on function push_snapshot(uuid, text, jsonb) from public;
grant execute on function register_store(text, uuid, text, text) to anon, authenticated;
grant execute on function push_snapshot(uuid, text, jsonb) to anon, authenticated;

-- 4. Afterwards, in the dashboard: Authentication → Users → "Add user" (your email + password),
--    and Authentication → Sign In / Providers → turn OFF "Allow new users to sign up".
