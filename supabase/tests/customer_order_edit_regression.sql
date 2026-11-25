-- Run after the customer-order-edit migration, in a transaction that is always rolled back.
begin;
do $test$
declare
  a uuid; b uuid; oid uuid; tok text; bid uuid; floor_no integer; tid uuid;
  result jsonb; before_rev integer; payload jsonb; options jsonb;
begin
  select f.building_id,f.floor_number into bid,floor_no from public.floors f join public.buildings b on b.id=f.building_id where b.is_active and f.floor_number>0 order by f.building_id,f.floor_number limit 1;
  select id into tid from public.delivery_trips where end_time>start_time limit 1;
  if bid is null or tid is null then raise exception 'Missing fixture location/trip'; end if;
  insert into public.products(sku,name,description,price_cents,is_active) values('T'||substr(replace(gen_random_uuid()::text,'-',''),1,12),'Temporary test A','',650,true) returning id into a;
  insert into public.products(sku,name,description,price_cents,is_active) values('T'||substr(replace(gen_random_uuid()::text,'-',''),1,12),'Temporary test B','',450,true) returning id into b;
  insert into public.inventory(product_id,physical_stock,reserved_stock) values(a,10,2),(b,10,0);
  insert into public.orders(order_no,customer_name,customer_phone,building_id,floor_number,room_number,payment_method,order_status,payment_status,total_cents,trip_id,trip_date)
  values('TEST-EDIT-'||gen_random_uuid()::text,'Temporary test','0100000000',bid,floor_no,'101','CASH','CONFIRMED','CASH_PENDING',1300,tid,public._trip_now()::date+1)
  returning id,tracking_token into oid,tok;
  insert into public.order_items(order_id,product_id,product_name,quantity,unit_price_cents) values(oid,a,'Temporary test A',2,650);
  insert into public.payments(order_id,method,amount_cents) values(oid,'CASH',1300);
  options:=public.get_order_edit_options(tok);
  if options->>'revision' <> '0' then raise exception 'Bad initial revision'; end if;
  payload:=jsonb_build_array(jsonb_build_object('product_id',a,'quantity',1),jsonb_build_object('product_id',b,'quantity',2));
  result:=public.edit_order_by_token(tok,0,payload,'No sauce',1550);
  if (result->>'total_cents')::int<>1550 or result->>'note'<>'No sauce' then raise exception 'Wrong returned order'; end if;
  if (select reserved_stock from public.inventory where product_id=a)<>1 or (select reserved_stock from public.inventory where product_id=b)<>2 then raise exception 'Wrong inventory adjustment'; end if;
  if (select amount_cents from public.payments where order_id=oid)<>1550 then raise exception 'Wrong payment amount'; end if;
  if (select customer_edit_revision from public.orders where id=oid)<>1 then raise exception 'Wrong revision'; end if;
  begin
    perform public.edit_order_by_token(tok,0,payload,'Stale',1550);
    raise exception 'Stale request accepted';
  exception when others then if sqlerrm<>'ORDER_EDIT_STALE' then raise; end if; end;
  begin
    perform public.edit_order_by_token(tok,1,jsonb_build_array(jsonb_build_object('product_id',b,'quantity',99)),null,44550);
    raise exception 'Oversized request accepted';
  exception when others then if sqlerrm not in ('INVALID_QUANTITY','INSUFFICIENT_STOCK') then raise; end if; end;
  begin
    perform public.edit_order_by_token(tok,1,payload,'Wrong total',1);
    raise exception 'Wrong amount accepted';
  exception when others then if sqlerrm<>'ORDER_EDIT_STALE' then raise; end if; end;
  update public.orders set order_status='PREPARING' where id=oid;
  begin
    perform public.edit_order_by_token(tok,1,payload,null,1550);
    raise exception 'Preparing order accepted';
  exception when others then if sqlerrm<>'ORDER_EDIT_LOCKED' then raise; end if; end;
  update public.orders set order_status='CONFIRMED' where id=oid;
  perform public.cancel_order_by_token(tok);
  perform public.cancel_order_by_token(tok);
  if (select reserved_stock from public.inventory where product_id=a)<>0 or (select reserved_stock from public.inventory where product_id=b)<>0 then raise exception 'Cancellation stock not released exactly once'; end if;
  if (select order_status from public.orders where id=oid)<>'CANCELLED' then raise exception 'Cancellation failed'; end if;
end $test$;
select 'ORDER_EDIT_REGRESSION_PASSED' as result;
rollback;
