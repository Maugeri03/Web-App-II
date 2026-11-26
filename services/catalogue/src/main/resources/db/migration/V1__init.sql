-- V1__init.sql
-- Initial catalogue schema: venues, seating plans, events, sessions, and pricing

CREATE TABLE venues (
    venue_id     TEXT PRIMARY KEY,
    organiser_id TEXT NOT NULL,
    name         TEXT NOT NULL,
    address      TEXT NOT NULL,
    time_zone    TEXT NOT NULL,
    version      BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);

CREATE TABLE seating_plans (
    plan_id   TEXT PRIMARY KEY,
    venue_id  TEXT NOT NULL REFERENCES venues (venue_id),
    version   BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    name      TEXT NOT NULL,
    -- Enables composite foreign key from sessions to enforce plan-venue ownership
    UNIQUE (plan_id, venue_id),
    -- Seating plan versions are sequential within a venue: two concurrent creations
    -- for the same venue must not both obtain the same version
    CONSTRAINT uq_seating_plans_venue_version UNIQUE (venue_id, version)
);

CREATE TABLE plan_seats (
    plan_id TEXT NOT NULL REFERENCES seating_plans (plan_id) ON DELETE CASCADE,
    seat_id TEXT NOT NULL,
    sector  TEXT NOT NULL,
    row     TEXT NOT NULL,
    number  TEXT NOT NULL,
    PRIMARY KEY (plan_id, seat_id),
    UNIQUE (plan_id, sector, row, number)
);

CREATE TABLE events (
    event_id          TEXT PRIMARY KEY,
    organiser_id      TEXT NOT NULL,
    title             TEXT NOT NULL,
    description       TEXT NOT NULL,
    publication_state TEXT NOT NULL CHECK (publication_state IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    version           BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1)
);

CREATE TABLE sessions (
    session_id     TEXT PRIMARY KEY,
    event_id       TEXT NOT NULL REFERENCES events (event_id),
    organiser_id   TEXT NOT NULL,
    venue_id       TEXT NOT NULL,
    plan_id        TEXT NOT NULL,
    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NULL,
    sales_open_at  TIMESTAMPTZ NOT NULL,
    sales_close_at TIMESTAMPTZ NOT NULL,
    time_zone      TEXT NOT NULL,
    starts_local   TEXT NOT NULL,
    state          TEXT NOT NULL CHECK (state IN ('DRAFT', 'PROVISIONING', 'READY', 'FAILED', 'CANCELLED')),
    version        BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Ensures the seating plan belongs to the selected venue
    CONSTRAINT fk_sessions_plan_venue
        FOREIGN KEY (plan_id, venue_id) REFERENCES seating_plans (plan_id, venue_id),

    -- Temporal constraints matching SessionWriteRequest / Session
    CONSTRAINT chk_sessions_sales_window
        CHECK (sales_open_at < sales_close_at AND sales_close_at <= starts_at),
    CONSTRAINT chk_sessions_ends_after_starts
        CHECK (ends_at IS NULL OR ends_at > starts_at)
);

-- Supports cursor-based pagination ordered by (starts_at, session_id)
CREATE INDEX idx_sessions_event_starts_id
    ON sessions (event_id, starts_at, session_id);

CREATE TABLE session_price_tiers (
    session_id    TEXT NOT NULL REFERENCES sessions (session_id) ON DELETE CASCADE,
    price_tier_id TEXT NOT NULL,
    name          TEXT NOT NULL,
    amount        TEXT NOT NULL CHECK (amount ~ '^(0|[1-9][0-9]*)\.[0-9]{2}$'),
    currency      TEXT NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    PRIMARY KEY (session_id, price_tier_id)
);

CREATE TABLE session_seat_prices (
    session_id    TEXT NOT NULL,
    plan_id       TEXT NOT NULL,
    seat_id       TEXT NOT NULL,
    price_tier_id TEXT NOT NULL,
    PRIMARY KEY (session_id, seat_id),

    -- The price tier must belong to the same session
    CONSTRAINT fk_seat_prices_tier
        FOREIGN KEY (session_id, price_tier_id)
        REFERENCES session_price_tiers (session_id, price_tier_id)
        ON DELETE CASCADE,

    -- The seat must belong to the session's seating plan
    CONSTRAINT fk_seat_prices_plan_seat
        FOREIGN KEY (plan_id, seat_id)
        REFERENCES plan_seats (plan_id, seat_id)
);