-- Customer delivery schedule update: evening delivery trips in Malaysia time.
-- Trip 1: 7:00 PM–7:30 PM; Trip 2: 9:00 PM–9:30 PM;
-- Trip 3: 11:00 PM–11:15 PM.
-- Apply after 20261118000001_customer_morning_delivery_schedule.sql.

begin;

update public.delivery_trips
set
  start_time = case trip_no
    when 1 then time '19:00'
    when 2 then time '21:00'
    when 3 then time '23:00'
  end,
  end_time = case trip_no
    when 1 then time '19:30'
    when 2 then time '21:30'
    when 3 then time '23:15'
  end,
  cutoff_minutes = 0
where trip_no in (1, 2, 3);

commit;

-- Verify:
-- select trip_no, label, start_time, end_time, cutoff_minutes
-- from public.delivery_trips where trip_no in (1, 2, 3) order by trip_no;
