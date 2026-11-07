-- MiniMart POS: shared product list between an owner's shops. Run ONCE after multitenant_setup.sql.
--
-- One shop is the "main" shop and publishes its products (names, prices, tax, supplier: never stock, sales or
-- customers). The owner's other shops follow it. Each owner has exactly one shared list; a shop can only read or
-- change the list of the owner it belongs to, and proves itself with its own secret like push_snapshot does.

create table if not exists owner_catalog (
  owner_id      uuid primary key references auth.users(id) on delete cascade,
  publisher_id  uuid references stores(id) on delete set null,
  version       bigint not null default 0,
  item_count    integer not null default 0,
  payload       jsonb not null default '[]'::jsonb,
  updated_at    timestamptz not null default now()
);
alter table owner_catalog enable row level security;   -- no policies: only the functions below touch it
revoke all on owner_catalog from anon, authenticated;

-- Shared by both functions: check the store's secret, return its owner. Slow on failure to blunt guessing.
create or replace function catalog_auth(p_store_id uuid, p_secret text) returns uuid
language plpgsql security definer set search_path = public, extensions as $$
declare s stores%rowtype;
begin
  select * into s from stores where id = p_store_id;
  if not found or s.secret_hash <> crypt(p_secret, s.secret_hash) then
    perform pg_sleep(1.5);
    raise exception 'Unknown store or wrong secret';
  end if;
  return s.owner_id;
end $$;
revoke all on function catalog_auth(uuid, text) from public, anon, authenticated;

-- The main shop publishes its list. Returns the new version number.
-- A different shop may only take over when p_take_over is true (the app asks the owner to confirm).
create or replace function publish_catalog(p_store_id uuid, p_secret text, p_items jsonb, p_take_over boolean default false)
returns bigint language plpgsql security definer set search_path = public, extensions as $$
declare o uuid; cur owner_catalog%rowtype; n integer; v bigint;
begin
  o := catalog_auth(p_store_id, p_secret);
  if jsonb_typeof(p_items) <> 'array' then raise exception 'The product list is not a list'; end if;
  n := jsonb_array_length(p_items);
  if n > 5000 then raise exception 'Too many products (limit 5000)'; end if;
  if pg_column_size(p_items) > 3000000 then raise exception 'Product list too large'; end if;

  insert into owner_catalog (owner_id) values (o) on conflict (owner_id) do nothing;
  select * into cur from owner_catalog where owner_id = o for update;
  if cur.publisher_id is not null and cur.publisher_id <> p_store_id and not coalesce(p_take_over, false) then
    raise exception 'Another shop already publishes the product list';
  end if;
  v := cur.version + 1;
  update owner_catalog set publisher_id = p_store_id, version = v, item_count = n, payload = p_items, updated_at = now()
   where owner_id = o;
  return v;
end $$;
revoke all on function publish_catalog(uuid, text, jsonb, boolean) from public;
grant execute on function publish_catalog(uuid, text, jsonb, boolean) to anon, authenticated;

-- A following shop asks for the list. Returns nothing new when it already has the latest version.
create or replace function fetch_catalog(p_store_id uuid, p_secret text, p_since bigint default 0)
returns table (version bigint, publisher_id uuid, item_count integer, items jsonb)
language plpgsql security definer set search_path = public, extensions as $$
declare o uuid;
begin
  o := catalog_auth(p_store_id, p_secret);
  return query select c.version, c.publisher_id, c.item_count, c.payload
    from owner_catalog c where c.owner_id = o and c.version > coalesce(p_since, 0);
end $$;
revoke all on function fetch_catalog(uuid, text, bigint) from public;
grant execute on function fetch_catalog(uuid, text, bigint) to anon, authenticated;

-- What the owner's dashboard shows: who publishes, the version, when.
create or replace function owner_catalog_status() returns table (publisher_name text, version bigint, item_count integer, updated_at timestamptz)
language sql security definer stable set search_path = public as $$
  select s.name, c.version, c.item_count, c.updated_at
  from owner_catalog c left join stores s on s.id = c.publisher_id where c.owner_id = auth.uid()
$$;
revoke all on function owner_catalog_status() from public, anon;
grant execute on function owner_catalog_status() to authenticated;
