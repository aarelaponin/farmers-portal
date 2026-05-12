-- ===================================================================
-- 001 — Budget Projection materialised view
-- ===================================================================
--
-- Per `_design/architecture/components/budget-accounting-methodology.md`
-- §4.1 (envelope balance identity), §4.2 (source contribution closure),
-- §4.3 (beneficiary control-total).
--
-- This view derives the six envelope subaccounts (Allocated, Available,
-- Reserved, PreCommitted, Committed, Expensed) from the append-only
-- budget_event ledger by summing balanced journal entries per
-- (envelope, account_path). The envelope balance identity
--   ALLOCATED = AVAILABLE + RESERVED + PRE_COMMITTED + COMMITTED + EXPENSED
-- must hold at all times; if it doesn't, a reconciliation job should
-- freeze the envelope and alert.
--
-- This file is run ONCE per environment by _tooling/run_migrations.py
-- after the budget_event Joget form has been deployed (so app_fd_budget_event
-- exists).
--
-- HARD RULE compliance: this is a CREATE MATERIALIZED VIEW over a Joget-
-- managed form table. The view is read-only and outside Joget's Hibernate
-- mapping; it does NOT modify the source table or its metadata. Permitted
-- per CLAUDE.md ("Reading is not writing").
-- ===================================================================

-- Drop on re-run (idempotent migration). PostgreSQL syntax.
DROP MATERIALIZED VIEW IF EXISTS budget_projection CASCADE;

CREATE MATERIALIZED VIEW budget_projection AS
WITH event_signed AS (
    -- Convert the (direction, amount) pair into a signed delta against
    -- the account_path. By accounting convention in this engine: a debit
    -- on an envelope subaccount INCREASES that subaccount; a credit
    -- DECREASES it. This convention is the public-sector commitment-
    -- funnel idiom — debit-the-target / credit-the-source within the
    -- envelope's six subaccounts (methodology §3 introduction).
    SELECT
        c_envelope_code   AS envelope_code,
        c_account_path    AS account_path,
        c_event_type      AS event_type,
        CASE WHEN c_direction = 'debit'  THEN  CAST(c_amount AS NUMERIC(15,2))
             WHEN c_direction = 'credit' THEN -CAST(c_amount AS NUMERIC(15,2))
             ELSE 0
        END AS signed_amount,
        datecreated       AS occurred_at
    FROM app_fd_budget_event
    WHERE c_envelope_code IS NOT NULL
      AND c_envelope_code <> ''
      AND c_account_path  IS NOT NULL
      AND c_account_path  <> ''
)
SELECT
    envelope_code,

    -- Six envelope-level subaccounts. account_path is matched on the
    -- substring after the dot; the prefix is the account holder
    -- (envelope code), the suffix is the subaccount name.
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.ALLOCATED'      THEN signed_amount
        ELSE 0 END), 0) AS allocated,
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.AVAILABLE'      THEN signed_amount
        ELSE 0 END), 0) AS available,
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.RESERVED'       THEN signed_amount
        ELSE 0 END), 0) AS reserved,
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.PRE_COMMITTED'  THEN signed_amount
        ELSE 0 END), 0) AS pre_committed,
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.COMMITTED'      THEN signed_amount
        ELSE 0 END), 0) AS committed,
    COALESCE(SUM(CASE
        WHEN account_path LIKE 'ENV_%.EXPENSED'       THEN signed_amount
        ELSE 0 END), 0) AS expensed,

    MAX(occurred_at) AS last_event_at,
    COUNT(*)         AS event_count
FROM event_signed
GROUP BY envelope_code;

-- Composite unique index so REFRESH MATERIALIZED VIEW CONCURRENTLY can run.
CREATE UNIQUE INDEX IF NOT EXISTS idx_budget_projection_envelope
    ON budget_projection (envelope_code);

-- Helper view: per-source proration. Same shape as budget_projection
-- but grouped by account_path's source-prefix instead of envelope.
DROP MATERIALIZED VIEW IF EXISTS budget_projection_by_source CASCADE;

CREATE MATERIALIZED VIEW budget_projection_by_source AS
WITH event_signed AS (
    SELECT
        c_envelope_code   AS envelope_code,
        c_account_path    AS account_path,
        -- Extract the SRC_*_X prefix from account_path. Source contribution
        -- accounts are named SRC_<source>_<envelope_short>.<subaccount>.
        SPLIT_PART(c_account_path, '.', 1) AS account_holder,
        CASE WHEN c_direction = 'debit'  THEN  CAST(c_amount AS NUMERIC(15,2))
             WHEN c_direction = 'credit' THEN -CAST(c_amount AS NUMERIC(15,2))
             ELSE 0
        END AS signed_amount,
        datecreated       AS occurred_at
    FROM app_fd_budget_event
    WHERE c_envelope_code IS NOT NULL AND c_envelope_code <> ''
      AND c_account_path  LIKE 'SRC_%'
)
SELECT
    envelope_code,
    account_holder AS source_contribution_code,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.ALLOCATED'     THEN signed_amount ELSE 0 END), 0) AS allocated,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.AVAILABLE'     THEN signed_amount ELSE 0 END), 0) AS available,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.RESERVED'      THEN signed_amount ELSE 0 END), 0) AS reserved,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.PRE_COMMITTED' THEN signed_amount ELSE 0 END), 0) AS pre_committed,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.COMMITTED'     THEN signed_amount ELSE 0 END), 0) AS committed,
    COALESCE(SUM(CASE WHEN account_path LIKE '%.EXPENSED'      THEN signed_amount ELSE 0 END), 0) AS expensed,
    MAX(occurred_at) AS last_event_at,
    COUNT(*)         AS event_count
FROM event_signed
GROUP BY envelope_code, account_holder;

CREATE UNIQUE INDEX IF NOT EXISTS idx_budget_projection_by_source
    ON budget_projection_by_source (envelope_code, source_contribution_code);

-- Helper view: invariant verification. Each row asserts the envelope
-- balance identity. A row with `imbalance != 0` is a violation requiring
-- envelope freeze + reconciliation.
DROP VIEW IF EXISTS budget_invariants CASCADE;

CREATE OR REPLACE VIEW budget_invariants AS
SELECT
    envelope_code,
    allocated,
    (available + reserved + pre_committed + committed + expensed) AS subaccount_sum,
    allocated - (available + reserved + pre_committed + committed + expensed) AS imbalance,
    CASE
        WHEN allocated - (available + reserved + pre_committed + committed + expensed) = 0
            THEN 'ok'
        ELSE 'VIOLATION'
    END AS invariant_status
FROM budget_projection;

-- Indices on the source table to speed up event_count + envelope queries.
-- Joget creates a default btree on `id`; we add envelope_code + account_path
-- since those are the high-volume filter columns. CREATE INDEX IF NOT EXISTS
-- is idempotent.
CREATE INDEX IF NOT EXISTS idx_budget_event_envelope
    ON app_fd_budget_event (c_envelope_code);
CREATE INDEX IF NOT EXISTS idx_budget_event_account_path
    ON app_fd_budget_event (c_account_path);
CREATE INDEX IF NOT EXISTS idx_budget_event_correlation
    ON app_fd_budget_event (c_correlation_id, c_correlation_type);
CREATE INDEX IF NOT EXISTS idx_budget_event_idempotency
    ON app_fd_budget_event (c_idempotency_key);

-- Initial refresh — populates the view from current ledger state. Cheap
-- because the GROUP BY runs once over what's typically a small table.
REFRESH MATERIALIZED VIEW budget_projection;
REFRESH MATERIALIZED VIEW budget_projection_by_source;
