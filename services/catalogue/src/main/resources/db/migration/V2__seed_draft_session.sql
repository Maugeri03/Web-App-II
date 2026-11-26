-- V2__seed_draft_session.sql
-- Seed reference data including a realistic DRAFT session for local development and integration testing

-- 1. Venue: Teatro Regio in Turin (Europe/Rome timezone)
INSERT INTO venues (venue_id, organiser_id, name, address, time_zone, version)
VALUES ('ven_teatro_regio_to', 'usr_org_live_nation_it', 'Teatro Regio Torino', 'Piazza Castello 215, 10124 Torino TO, Italy', 'Europe/Rome',1);

-- 2. Immutable seating plan for the venue
INSERT INTO seating_plans (plan_id, venue_id, version, name)
VALUES ('plan_regio_main_hall_v1', 'ven_teatro_regio_to', 1, 'Sala Grande - Platea e Palchi');

-- 3. Numbered physical seats across two sectors (Platea and Galleria)
INSERT INTO plan_seats (plan_id, seat_id, sector, row, number)
VALUES
    ('plan_regio_main_hall_v1', 'seat_plt_a1', 'Platea', 'A', '1'),
    ('plan_regio_main_hall_v1', 'seat_plt_a2', 'Platea', 'A', '2'),
    ('plan_regio_main_hall_v1', 'seat_gal_b1', 'Galleria', 'B', '1'),
    ('plan_regio_main_hall_v1', 'seat_gal_b2', 'Galleria', 'B', '2');

-- 4. Published parent event
INSERT INTO events (event_id, organiser_id, title, description, publication_state, version)
VALUES ('evt_ludovico_einaudi_2027', 'usr_org_live_nation_it','Ludovico Einaudi - Summer Night Tour 2027', 'An evening piano and ensemble concert in Turin featuring selections from In a Time Lapse and Underwater.', 'PUBLISHED',1);

-- 5. Session in DRAFT state (21:00 CEST = UTC+02:00 in July for Europe/Rome)
INSERT INTO sessions (session_id, event_id, organiser_id, venue_id, plan_id, starts_at, ends_at, sales_open_at, sales_close_at, time_zone, starts_local, state, version)
VALUES ('ses_einaudi_to_20270715', 'evt_ludovico_einaudi_2027', 'usr_org_live_nation_it', 'ven_teatro_regio_to', 'plan_regio_main_hall_v1', '2027-07-15T21:00:00+02:00', '2027-07-15T23:15:00+02:00', '2027-05-01T10:00:00+02:00', '2027-07-15T20:00:00+02:00', 'Europe/Rome', '2027-07-15T21:00:00', 'DRAFT', 1);

-- 6. Session price tiers in EUR (matching Money pattern ^(0|[1-9][0-9]*)\.[0-9]{2}$)
INSERT INTO session_price_tiers (session_id, price_tier_id, name, amount, currency)
VALUES
    ('ses_einaudi_to_20270715', 'tier_platea_vip', 'Platea Primo Settore', '75.00', 'EUR'),
    ('ses_einaudi_to_20270715', 'tier_galleria_std', 'Galleria Standard', '45.00', 'EUR');

-- 7. Complete seat-to-tier assignment for every seat in the selected seating plan
INSERT INTO session_seat_prices (session_id, plan_id, seat_id, price_tier_id)
VALUES
    ('ses_einaudi_to_20270715', 'plan_regio_main_hall_v1', 'seat_plt_a1', 'tier_platea_vip'),
    ('ses_einaudi_to_20270715', 'plan_regio_main_hall_v1', 'seat_plt_a2', 'tier_platea_vip'),
    ('ses_einaudi_to_20270715', 'plan_regio_main_hall_v1', 'seat_gal_b1', 'tier_galleria_std'),
    ('ses_einaudi_to_20270715', 'plan_regio_main_hall_v1', 'seat_gal_b2', 'tier_galleria_std');