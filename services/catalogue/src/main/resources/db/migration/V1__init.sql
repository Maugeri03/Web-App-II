-- V1__init.sql
-- Initial catalogue schema: venues, seating plans, events, sessions, pricing,
-- idempotency records and the session provisioning outbox

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

-- Idempotency records for mutating POST endpoints: keys are scoped to caller and operation,
-- retained for 24 hours (purged by the application), and store the serialised command result
CREATE TABLE idempotency_records (
    caller_id         TEXT NOT NULL,
    endpoint          TEXT NOT NULL,
    idempotency_key   TEXT NOT NULL,
    request_hash      TEXT NOT NULL,
    response_body     TEXT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (caller_id, endpoint, idempotency_key)
);

-- Supports the periodic deletion of expired records
CREATE INDEX idx_idempotency_records_created_at
    ON idempotency_records (created_at);

-- Transactional outbox driving asynchronous inventory provisioning for published sessions.
-- While a task is IN_PROGRESS, next_attempt_at is the expiry of the worker lease.
CREATE TABLE session_provisioning_outbox (
    outbox_id         TEXT PRIMARY KEY,
    session_id        TEXT NOT NULL UNIQUE REFERENCES sessions (session_id) ON DELETE CASCADE,
    status            TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    attempt_count     INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error        TEXT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Supports efficient worker polling for due tasks
CREATE INDEX idx_outbox_status_next_attempt
    ON session_provisioning_outbox (status, next_attempt_at);
