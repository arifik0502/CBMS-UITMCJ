-- Customer delivery schedule update: morning delivery trips in Malaysia time.
-- Trip 1: 7:00 AM–7:30 AM; Trip 2: 9:00 AM–9:30 AM;
-- Trip 3: 11:00 AM–11:15 AM.
-- Apply after the existing delivery-trip migrations.

begin;

update public.delivery_trips
set
  start_time = case trip_no
    when 1 then time '07:00'
    when 2 then time '09:00'
    when 3 then time '11:00'
  end,
  end_time = case trip_no
    when 1 then time '07:30'
    when 2 then time '09:30'
    when 3 then time '11:15'
  end,
  cutoff_minutes = 0
where trip_no in (1, 2, 3);

commit;

-- Verify:
-- select trip_no, label, start_time, end_time, cutoff_minutes
-- from public.delivery_trips where trip_no in (1, 2, 3) order by trip_no;
