-- MiniMart POS — hosted multi-owner backend (Supabase). Run ONCE by the operator who hosts it
-- (Supabase → SQL Editor, or `apply_migration`). Shop owners never run SQL: they sign up on the web dashboard,
-- press "Add a shop", and type the short code it shows into the app.
--
-- Model
--   * An owner is a Supabase Auth user. Every store belongs to exactly one owner.
--   * An owner can read ONLY the snapshots of their own stores (row-level security, checked by owns_store()).
--   * A phone registers with a one-time code the signed-in owner generated (valid 30 minutes). From then on it
--     proves itself with a random secret of which only a bcrypt hash is stored.
--   * Nobody with just the public anon key can read anything.
--
-- Also in the Supabase dashboard (not SQL): Authentication → Providers → Email: turn ON "Confirm email",
-- and set a rate limit for sign-ups. Keep sign-ups enabled: that is how owners join.

create extension if not exists pgcrypto with schema extensions;

create table if not exists stores (
  id          uuid primary key,
  owner_id    uuid not null references auth.users(id) on delete cascade,
  name        text not null,
  secret_hash text not null,
  created_at  timestamptz not null default now()
);
create index if not exists stores_owner_idx on stores(owner_id);

create table if not exists store_snapshots (
  store_id   uuid primary key references stores(id) on delete cascade,
  store_name text not null,
  payload    jsonb not null,
  pushed_at  timestamptz not null default now()
);

create table if not exists link_codes (
  code_hash  text primary key,
  owner_id   uuid not null references auth.users(id) on delete cascade,
  expires_at timestamptz not null,
  used_at    timestamptz
);
create index if not exists link_codes_owner_idx on link_codes(owner_id);

alter table stores          enable row level security;
alter table store_snapshots enable row level security;
alter table link_codes      enable row level security;   -- no policies: only the functions below touch them

-- Is this store mine? (security definer so the check can see `stores`, which has no policies.)
create or replace function owns_store(p_store_id uuid) returns boolean
language sql security definer stable set search_path = public as $$
  select exists (select 1 from stores where id = p_store_id and owner_id = auth.uid())
$$;
revoke all on function owns_store(uuid) from public, anon;
grant execute on function owns_store(uuid) to authenticated;

drop policy if exists "owners read their snapshots" on store_snapshots;
create policy "owners read their snapshots" on store_snapshots
  for select to authenticated using (owns_store(store_id));

revoke all on stores, store_snapshots, link_codes from anon, authenticated;
grant select on store_snapshots to authenticated;

-- 1. A signed-in owner asks for a code to connect one more shop. Returned once, e.g. "K7QM-4TX9".
create or replace function owner_create_link_code() returns text
language plpgsql security definer set search_path = public, extensions as $$
declare
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';   -- no 0/O/1/I/L: easy to read out loud
  raw bytea; code text := ''; i int;
begin
  if auth.uid() is null then raise exception 'Not signed in'; end if;
  delete from link_codes where owner_id = auth.uid() and (expires_at < now() or used_at is not null);
  if (select count(*) from link_codes where owner_id = auth.uid()) >= 10 then
    raise exception 'Too many unused codes; use or wait for the earlier ones to expire';
  end if;
  raw := extensions.gen_random_bytes(8);
  for i in 0..7 loop
    code := code || substr(alphabet, (get_byte(raw, i) % 31) + 1, 1);
  end loop;
  insert into link_codes (code_hash, owner_id, expires_at)
  values (encode(extensions.digest(code, 'sha256'), 'hex'), auth.uid(), now() + interval '30 minutes');
  return substr(code, 1, 4) || '-' || substr(code, 5, 4);
end $$;
revoke all on function owner_create_link_code() from public, anon;
grant execute on function owner_create_link_code() to authenticated;

-- 2. The phone registers with that code (once per store).
create or replace function register_store_with_code(p_code text, p_store_id uuid, p_name text, p_secret text)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare c link_codes%rowtype; norm text;
begin
  norm := upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  select * into c from link_codes
    where code_hash = encode(extensions.digest(norm, 'sha256'), 'hex')
      and used_at is null and expires_at > now()
    for update;
  if not found then
    perform pg_sleep(1.5);   -- slows down guessing; the code space is ~8.5e11 and codes live 30 minutes
    raise exception 'Invalid or expired code';
  end if;
  if length(coalesce(p_secret, '')) < 24 then raise exception 'Store secret too short'; end if;
  if exists (select 1 from stores where id = p_store_id) then raise exception 'This store is already registered'; end if;
  if (select count(*) from stores where owner_id = c.owner_id) >= 25 then raise exception 'This account has reached its shop limit'; end if;
  update link_codes set used_at = now() where code_hash = c.code_hash;
  insert into stores (id, owner_id, name, secret_hash)
  values (p_store_id, c.owner_id, left(coalesce(nullif(trim(p_name), ''), 'Shop'), 80), crypt(p_secret, gen_salt('bf')));
end $$;
revoke all on function register_store_with_code(text, uuid, text, text) from public;
grant execute on function register_store_with_code(text, uuid, text, text) to anon, authenticated;

-- 3. A registered store uploads its latest snapshot (same as the single-owner setup).
create or replace function push_snapshot(p_store_id uuid, p_secret text, p_payload jsonb)
returns void language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    perform pg_sleep(1.5);
    raise exception 'Unknown store or wrong secret';
  end if;
  if pg_column_size(p_payload) > 400000 then raise exception 'Snapshot too large'; end if;
  if coalesce(p_payload->>'storeName', '') <> '' and left(p_payload->>'storeName', 80) <> s.name then
    s.name := left(p_payload->>'storeName', 80);
    update stores set name = s.name where id = p_store_id;
  end if;
  insert into store_snapshots (store_id, store_name, payload, pushed_at)
  values (p_store_id, s.name, p_payload, now())
  on conflict (store_id) do update
    set store_name = excluded.store_name, payload = excluded.payload, pushed_at = now();
end $$;
revoke all on function push_snapshot(uuid, text, jsonb) from public;
grant execute on function push_snapshot(uuid, text, jsonb) to anon, authenticated;

-- 4. Owner tools from the dashboard.
create or replace function owner_my_stores() returns table (id uuid, name text, created_at timestamptz, last_push timestamptz)
language sql security definer stable set search_path = public as $$
  select s.id, s.name, s.created_at, sn.pushed_at
  from stores s left join store_snapshots sn on sn.store_id = s.id
  where s.owner_id = auth.uid() order by s.created_at
$$;

create or replace function owner_delete_store(p_store_id uuid) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'Not signed in'; end if;
  delete from stores where id = p_store_id and owner_id = auth.uid();   -- snapshots cascade
end $$;

-- Deleting an account removes all of its shops and data (the owner's right to leave).
create or replace function owner_delete_account() returns void
language plpgsql security definer set search_path = public, auth as $$
begin
  if auth.uid() is null then raise exception 'Not signed in'; end if;
  delete from auth.users where id = auth.uid();   -- stores, snapshots and codes cascade
end $$;

revoke all on function owner_my_stores(), owner_delete_store(uuid), owner_delete_account() from public, anon;
grant execute on function owner_my_stores(), owner_delete_store(uuid), owner_delete_account() to authenticated;
