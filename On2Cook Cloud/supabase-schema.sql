create extension if not exists pgcrypto;

create table if not exists public.facilities (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references public.facilities(id) on delete set null,
  name text not null,
  facility_type text not null default 'kitchen',
  created_at timestamptz not null default now()
);

create table if not exists public.profiles (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete set null,
  email text not null unique,
  display_name text not null,
  role text not null check (role in ('main_admin', 'admin', 'kitchen_manager', 'operator')),
  created_at timestamptz not null default now()
);

create table if not exists public.recipes (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete cascade,
  base_recipe_id uuid references public.recipes(id) on delete set null,
  name text not null,
  firmware_name text not null,
  category text,
  source text not null default 'custom',
  image_url text,
  zip_url text,
  recipe_json jsonb not null,
  is_selected boolean not null default true,
  is_final boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (facility_id, firmware_name)
);

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete cascade,
  browser_device_id text not null,
  bluetooth_name text,
  display_name text not null,
  serial_photo_url text,
  enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (facility_id, browser_device_id)
);

create table if not exists public.device_recipe_permissions (
  id uuid primary key default gen_random_uuid(),
  device_id uuid references public.devices(id) on delete cascade,
  recipe_id uuid references public.recipes(id) on delete cascade,
  enabled boolean not null default true,
  unique (device_id, recipe_id)
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete cascade,
  external_order_id text not null,
  source text not null default 'POS',
  item_name text not null,
  recipe_firmware_name text not null,
  quantity text not null,
  special_instructions text,
  status text not null default 'pending',
  assigned_device_id uuid references public.devices(id) on delete set null,
  payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists public.device_logs (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete cascade,
  device_id uuid references public.devices(id) on delete cascade,
  log_name text,
  log_body text,
  summary jsonb not null default '{}'::jsonb,
  captured_at timestamptz not null default now()
);

create table if not exists public.settings (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid references public.facilities(id) on delete cascade,
  key text not null,
  value jsonb not null,
  updated_at timestamptz not null default now(),
  unique (facility_id, key)
);
