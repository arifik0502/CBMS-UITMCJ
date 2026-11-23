-- CBMS website customization ("Website" tab in the admin app)
--
-- An active OWNER or ADMIN edits the customer website from the Android admin app without touching code.
-- Settings are DATA only (text, colours, section order, image URLs). No HTML or script is ever stored,
-- so the owner can not break the site and a stolen session can not inject code into it.
--
--   * public.site_settings            one row: published config, draft config, version
--   * public.site_config_history      last 15 published versions (for "go back")
--   * get_site_config()               anon + authenticated: what the website renders
--   * admin_get_site_state()          ADMIN: published + draft
--   * admin_save_site_draft(jsonb)    ADMIN: validate + save draft (not live)
--   * admin_publish_site()            ADMIN: draft -> live
--   * admin_discard_site_draft()      ADMIN: throw the draft away
--   * admin_list_site_history()       ADMIN: earlier published versions
--   * admin_restore_site_version(uuid) ADMIN: copy an earlier version into the draft
--   * storage bucket `site-assets`    PUBLIC read, one JPEG up to 1 MB per upload, OWNER/ADMIN upload only
--
-- Empty published config ('{}') means "use the website's built-in design". Safe to re-run.

begin;

create or replace function public._require_active_admin()
returns void
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if auth.uid() is null
     or not exists (
       select 1 from public.profiles p
       where p.id = auth.uid() and p.role::text in ('OWNER', 'ADMIN') and p.is_active is true
     )
  then
    raise exception 'FORBIDDEN';
  end if;
end;
$$;
revoke all on function public._require_active_admin() from public, anon, authenticated;

create table if not exists public.site_settings (
  id               smallint primary key default 1 check (id = 1),
  published        jsonb not null default '{}'::jsonb,
  draft            jsonb,
  version          integer not null default 0,
  published_at     timestamptz,
  published_by     uuid references auth.users (id) on delete set null,
  draft_updated_at timestamptz
);

create table if not exists public.site_config_history (
  id           uuid primary key default gen_random_uuid(),
  version      integer not null,
  config       jsonb not null,
  published_at timestamptz not null default now(),
  published_by uuid references auth.users (id) on delete set null
);

insert into public.site_settings (id) values (1) on conflict (id) do nothing;

alter table public.site_settings enable row level security;
alter table public.site_config_history enable row level security;
revoke all on public.site_settings from anon, authenticated;
revoke all on public.site_config_history from anon, authenticated;

-- ---------- validation: rebuild the config from a whitelist, reject anything odd ----------

create or replace function public._site_text(p jsonb, p_key text, p_max integer)
returns text
language sql
immutable
as $$
  select left(btrim(regexp_replace(coalesce(p ->> p_key, ''), '[\u0001-\u0008\u000B\u000C\u000E-\u001F]', '', 'g')), p_max);
$$;

create or replace function public._site_color(p jsonb, p_key text, p_default text)
returns text
language plpgsql
immutable
as $$
declare v text := coalesce(p ->> p_key, '');
begin
  if v = '' then return p_default; end if;
  if v !~ '^#[0-9a-fA-F]{6}$' then raise exception 'INVALID_COLOR'; end if;
  return upper(v);
end;
$$;

create or replace function public._site_link(p_value text)
returns text
language plpgsql
immutable
as $$
begin
  if p_value is null or p_value = '' then return ''; end if;
  if char_length(p_value) > 200 or p_value ~ '\s' then raise exception 'INVALID_LINK'; end if;
  if p_value ~ '^/[^/]' or p_value = '/' or p_value ~ '^https://[^/]+' then return p_value; end if;
  raise exception 'INVALID_LINK';
end;
$$;

create or replace function public._site_image(p_value text)
returns text
language plpgsql
immutable
as $$
begin
  if p_value is null or p_value = '' then return null; end if;
  if p_value !~ '^https://[a-z0-9.-]+/storage/v1/object/public/site-assets/[0-9]{10,16}-[0-9a-f]{8}\.jpg$' then
    raise exception 'INVALID_IMAGE';
  end if;
  return p_value;
end;
$$;

create or replace function public._site_config_clean(p jsonb)
returns jsonb
language plpgsql
immutable
set search_path = public
as $$
declare
  v_theme    jsonb;
  v_banner   jsonb;
  v_tone     text;
  v_font     text;
  v_sections jsonb := '[]'::jsonb;
  s          jsonb;
  v_type     text;
  v_id       text;
  v_ids      text[] := '{}';
  v_fixed    text[] := '{}';
  v_custom   integer := 0;
  v_menu_ok  boolean := false;
  v_items    jsonb;
  v_item     text;
  v_vis      boolean;
  v_clean    jsonb;
begin
  if p is null or jsonb_typeof(p) <> 'object' or octet_length(p::text) > 30000 then
    raise exception 'INVALID_CONFIG';
  end if;

  v_theme := coalesce(p -> 'theme', '{}'::jsonb);
  if jsonb_typeof(v_theme) <> 'object' then raise exception 'INVALID_CONFIG'; end if;
  v_font := coalesce(nullif(v_theme ->> 'font', ''), 'default');
  if v_font not in ('default', 'friendly', 'classic', 'modern') then raise exception 'INVALID_FONT'; end if;

  v_banner := coalesce(p -> 'banner', '{}'::jsonb);
  if jsonb_typeof(v_banner) <> 'object' then raise exception 'INVALID_CONFIG'; end if;
  v_tone := coalesce(nullif(v_banner ->> 'tone', ''), 'info');
  if v_tone not in ('info', 'promo', 'warn') then raise exception 'INVALID_CONFIG'; end if;

  if jsonb_typeof(coalesce(p -> 'sections', '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p -> 'sections', '[]'::jsonb)) > 24 then
    raise exception 'TOO_MANY_SECTIONS';
  end if;

  for s in select value from jsonb_array_elements(coalesce(p -> 'sections', '[]'::jsonb)) loop
    if jsonb_typeof(s) <> 'object' then raise exception 'INVALID_CONFIG'; end if;
    v_type := s ->> 'type';
    v_id   := s ->> 'id';
    if v_id is null or v_id !~ '^[a-z0-9_]{1,24}$' or v_id = any (v_ids) then raise exception 'INVALID_SECTION'; end if;
    if v_type not in ('hero', 'menu', 'steps', 'trips', 'buildings', 'support', 'text', 'image', 'button') then
      raise exception 'INVALID_SECTION';
    end if;
    v_ids := v_ids || v_id;
    v_vis := coalesce((s ->> 'visible')::boolean, true);

    if v_type in ('hero', 'menu', 'steps', 'trips', 'buildings', 'support') then
      if v_type = any (v_fixed) then raise exception 'INVALID_SECTION'; end if;
      v_fixed := v_fixed || v_type;
    else
      v_custom := v_custom + 1;
      if v_custom > 10 then raise exception 'TOO_MANY_CUSTOM'; end if;
    end if;
    if v_type = 'menu' and v_vis then v_menu_ok := true; end if;

    v_clean := jsonb_build_object('id', v_id, 'type', v_type, 'visible', v_vis);

    if v_type = 'hero' then
      v_clean := v_clean || jsonb_build_object(
        'headline', public._site_text(s, 'headline', 120),
        'image_url', public._site_image(s ->> 'image_url'));
    elsif v_type = 'menu' then
      v_clean := v_clean || jsonb_build_object(
        'title', public._site_text(s, 'title', 40),
        'image_url', public._site_image(s ->> 'image_url'));
      v_items := '[]'::jsonb;
      if jsonb_typeof(coalesce(s -> 'items', '[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(s -> 'items', '[]'::jsonb)) > 8 then
        raise exception 'INVALID_SECTION';
      end if;
      for v_item in select jsonb_array_elements_text(coalesce(s -> 'items', '[]'::jsonb)) loop
        v_item := left(btrim(regexp_replace(v_item, '[\u0001-\u0008\u000B\u000C\u000E-\u001F]', '', 'g')), 160);
        if v_item <> '' then v_items := v_items || to_jsonb(v_item); end if;
      end loop;
      v_clean := v_clean || jsonb_build_object('title', public._site_text(s, 'title', 40), 'items', v_items);
    elsif v_type = 'trips' then
      v_clean := v_clean || jsonb_build_object('title', public._site_text(s, 'title', 40), 'body', public._site_text(s, 'body', 200));
    elsif v_type = 'buildings' then
      v_clean := v_clean || jsonb_build_object('body', public._site_text(s, 'body', 200));
    elsif v_type = 'support' then
      v_clean := v_clean || jsonb_build_object('body', public._site_text(s, 'body', 120), 'label', public._site_text(s, 'label', 30));
    elsif v_type = 'text' then
      v_clean := v_clean || jsonb_build_object('title', public._site_text(s, 'title', 60), 'body', public._site_text(s, 'body', 600));
    elsif v_type = 'image' then
      v_clean := v_clean || jsonb_build_object(
        'image_url', public._site_image(s ->> 'image_url'),
        'caption', public._site_text(s, 'caption', 120));
    elsif v_type = 'button' then
      v_clean := v_clean || jsonb_build_object(
        'label', public._site_text(s, 'label', 30),
        'to', public._site_link(public._site_text(s, 'to', 200)));
    end if;

    v_sections := v_sections || v_clean;
  end loop;

  if jsonb_array_length(v_sections) > 0 and not v_menu_ok then
    raise exception 'MENU_REQUIRED';
  end if;

  return jsonb_build_object(
    'v', 1,
    'brand_name', public._site_text(p, 'brand_name', 30),
    'theme', jsonb_build_object(
      'bg',     public._site_color(v_theme, 'bg',     '#FFC93C'),
      'ink',    public._site_color(v_theme, 'ink',    '#1B1410'),
      'accent', public._site_color(v_theme, 'accent', '#D7263D'),
      'good',   public._site_color(v_theme, 'good',   '#2B7A3D'),
      'paper',  public._site_color(v_theme, 'paper',  '#FFFDF7'),
      'font',   v_font),
    'banner', jsonb_build_object(
      'enabled',    coalesce((v_banner ->> 'enabled')::boolean, false),
      'text',       public._site_text(v_banner, 'text', 120),
      'tone',       v_tone,
      'link_label', public._site_text(v_banner, 'link_label', 30),
      'link_to',    public._site_link(public._site_text(v_banner, 'link_to', 200))),
    'sections', v_sections,
    'footer_note', public._site_text(p, 'footer_note', 160)
  );
end;
$$;
revoke all on function public._site_config_clean(jsonb) from public, anon, authenticated;
revoke all on function public._site_text(jsonb, text, integer) from public, anon, authenticated;
revoke all on function public._site_color(jsonb, text, text) from public, anon, authenticated;
revoke all on function public._site_link(text) from public, anon, authenticated;
revoke all on function public._site_image(text) from public, anon, authenticated;

-- ---------- public read ----------

create or replace function public.get_site_config()
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'version', s.version,
    'config',  case when s.published = '{}'::jsonb then null else s.published end
  )
  from public.site_settings s where s.id = 1;
$$;

-- ---------- owner RPCs ----------

create or replace function public.admin_get_site_state()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare s public.site_settings;
begin
  perform public._require_active_admin();
  select * into s from public.site_settings where id = 1;
  return jsonb_build_object(
    'version', s.version,
    'published', case when s.published = '{}'::jsonb then null else s.published end,
    'draft', s.draft,
    'has_unpublished', s.draft is not null and s.draft is distinct from s.published,
    'published_at', s.published_at
  );
end;
$$;

create or replace function public.admin_save_site_draft(p_config jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._require_active_admin();
  update public.site_settings
     set draft = public._site_config_clean(p_config),
         draft_updated_at = now()
   where id = 1;
end;
$$;

create or replace function public.admin_publish_site()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare s public.site_settings; v_new integer;
begin
  perform public._require_active_admin();
  select * into s from public.site_settings where id = 1 for update;
  if s.draft is null then raise exception 'NO_DRAFT'; end if;

  if s.published <> '{}'::jsonb then
    insert into public.site_config_history (version, config, published_at, published_by)
    values (s.version, s.published, coalesce(s.published_at, now()), s.published_by);
  end if;

  v_new := s.version + 1;
  update public.site_settings
     set published = s.draft, draft = null, version = v_new,
         published_at = now(), published_by = auth.uid(), draft_updated_at = null
   where id = 1;

  delete from public.site_config_history
   where id in (
     select id from public.site_config_history order by published_at desc offset 15
   );
  return v_new;
end;
$$;

create or replace function public.admin_discard_site_draft()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public._require_active_admin();
  update public.site_settings set draft = null, draft_updated_at = null where id = 1;
end;
$$;

create or replace function public.admin_list_site_history()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  perform public._require_active_admin();
  return coalesce((
    select jsonb_agg(jsonb_build_object('id', h.id, 'version', h.version, 'published_at', h.published_at)
                     order by h.published_at desc)
    from (select * from public.site_config_history order by published_at desc limit 15) h
  ), '[]'::jsonb);
end;
$$;

create or replace function public.admin_restore_site_version(p_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare v_cfg jsonb;
begin
  perform public._require_active_admin();
  select config into v_cfg from public.site_config_history where id = p_id;
  if v_cfg is null then raise exception 'VERSION_NOT_FOUND'; end if;
  update public.site_settings set draft = v_cfg, draft_updated_at = now() where id = 1;
end;
$$;

revoke all on function public.get_site_config() from public;
revoke all on function public.admin_get_site_state() from public, anon;
revoke all on function public.admin_save_site_draft(jsonb) from public, anon;
revoke all on function public.admin_publish_site() from public, anon;
revoke all on function public.admin_discard_site_draft() from public, anon;
revoke all on function public.admin_list_site_history() from public, anon;
revoke all on function public.admin_restore_site_version(uuid) from public, anon;
grant execute on function public.get_site_config() to anon, authenticated;
grant execute on function public.admin_get_site_state() to authenticated;
grant execute on function public.admin_save_site_draft(jsonb) to authenticated;
grant execute on function public.admin_publish_site() to authenticated;
grant execute on function public.admin_discard_site_draft() to authenticated;
grant execute on function public.admin_list_site_history() to authenticated;
grant execute on function public.admin_restore_site_version(uuid) to authenticated;

-- ---------- public image bucket (hero + picture blocks) ----------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('site-assets', 'site-assets', true, 1048576, array['image/jpeg'])
on conflict (id) do update
  set public = true,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "cbms_owner_uploads_site_assets" on storage.objects;
create policy "cbms_owner_uploads_site_assets"
on storage.objects
for insert
to authenticated
with check (
  bucket_id = 'site-assets'
  and name ~ '^[0-9]{10,16}-[0-9a-f]{8}\.jpg$'
  and exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text in ('OWNER', 'ADMIN') and p.is_active is true
  )
);

drop policy if exists "cbms_owner_deletes_site_assets" on storage.objects;
create policy "cbms_owner_deletes_site_assets"
on storage.objects
for delete
to authenticated
using (
  bucket_id = 'site-assets'
  and exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role::text in ('OWNER', 'ADMIN') and p.is_active is true
  )
);

commit;

notify pgrst, 'reload schema';
