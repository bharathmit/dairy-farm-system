-- Module 8 (Canvas 4): feed items (reuse item / item_txn), concentrate formulas, ration rules.

-- Feed purchases keep how they were paid (for the Finance module); feeding rows keep their session and save group.
ALTER TABLE item_txn
    ADD COLUMN payment_method VARCHAR(14) CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'OTHER')),
    ADD COLUMN paid           BOOLEAN,
    ADD COLUMN session        VARCHAR(2) CHECK (session IN ('AM', 'PM')),
    ADD COLUMN batch_id       UUID;
CREATE INDEX ix_item_txn_feeding ON item_txn (farm_id, txn_date, session) WHERE source_type = 'FEEDING';
CREATE INDEX ix_item_txn_batch ON item_txn (batch_id) WHERE batch_id IS NOT NULL;

ALTER TABLE item ADD CONSTRAINT ck_item_feed_category CHECK (item_type <> 'FEED' OR category IN
    ('CONCENTRATE_INGREDIENT', 'GREEN_FODDER', 'DRY_FODDER', 'SILAGE', 'MINERAL', 'MIX'));
CREATE UNIQUE INDEX ux_item_feed_name ON item (farm_id, lower(name)) WHERE item_type = 'FEED';

-- A concentrate recipe: kg of each ingredient for one batch of the MIX item.
CREATE TABLE feed_formula (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id      BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    name         VARCHAR(120)   NOT NULL,
    mix_item_id  BIGINT         NOT NULL REFERENCES item (id),
    batch_kg     NUMERIC(10, 2) NOT NULL CHECK (batch_kg > 0),
    targets      JSONB          NOT NULL DEFAULT '{}',   -- dmPct, cpPct, tdnPct, caPct, pPct, costMin, costMax
    notes        TEXT,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by   UUID
);
CREATE UNIQUE INDEX ux_feed_formula_name ON feed_formula (farm_id, lower(name));
CREATE INDEX ix_feed_formula_mix ON feed_formula (mix_item_id);

CREATE TABLE feed_formula_line (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    formula_id  BIGINT         NOT NULL REFERENCES feed_formula (id) ON DELETE CASCADE,
    item_id     BIGINT         NOT NULL REFERENCES item (id),
    kg          NUMERIC(10, 3) NOT NULL CHECK (kg > 0),
    sort_order  INT            NOT NULL DEFAULT 0,
    UNIQUE (formula_id, item_id)
);
CREATE INDEX ix_feed_formula_line_item ON feed_formula_line (item_id);

-- Ration rule per animal category, versioned by the date it takes effect. No row = defaults in code.
CREATE TABLE ration_rule (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id         BIGINT       NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    category        VARCHAR(10)  NOT NULL CHECK (category IN ('CALF', 'HEIFER', 'MILKING', 'DRY', 'BULL')),
    effective_from  DATE         NOT NULL,
    params          JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    UNIQUE (farm_id, category, effective_from)
);
