-- Removes what the e2e tests leave in a local database: their users (e2e-*@example.com) and events ("E2E ...").
-- docker exec -i eventsbooking-postgres psql -U postgres -d eventsbooking -v ON_ERROR_STOP=1 < frontend/e2e/cleanup.sql
begin;
create temp table u as select id from users where email like 'e2e-%@example.com';
create temp table e as select id from events where title like 'E2E %' or organizer_id in (select id from u);
delete from notifications where user_id in (select id from u) or target_event_id in (select id from e);
delete from idempotency_keys where user_id in (select id from u) or event_id in (select id from e);
delete from favorites where user_id in (select id from u);
delete from waitlist where user_id in (select id from u);
delete from bookings where user_id in (select id from u);
delete from notification_preferences where user_id in (select id from u);
delete from events where id in (select id from e);
delete from users where id in (select id from u);
select (select count(*) from events) events_left, (select count(*) from users) users_left;
commit;
