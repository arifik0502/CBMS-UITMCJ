-- Allow only active CBMS OWNER accounts to create signed URLs for private receipts.
-- The receipts bucket remains PRIVATE; customers do not receive read access.

begin;

drop policy if exists "cbms_active_owner_can_read_private_receipts" on storage.objects;

create policy "cbms_active_owner_can_read_private_receipts"
on storage.objects
for select
to authenticated
using (
    bucket_id = 'receipts'
    and exists (
        select 1
        from public.profiles p
        where p.id = auth.uid()
          and p.role = 'OWNER'
          and p.is_active is true
    )
);

commit;
