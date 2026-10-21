-- Module 5 to 7 (Canvas 2): contacts, milk records and grading rules, weight/BCS and health events.

-- People the farm deals with: milk buyers, vets, AI technicians, suppliers.
CREATE TABLE contact (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id     BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    name        VARCHAR(120)  NOT NULL,
    roles       TEXT[]        NOT NULL CHECK (cardinality(roles) BETWEEN 1 AND 5
                              AND roles <@ ARRAY['BUYER', 'VET', 'AI_TECH', 'SUPPLIER', 'OTHER']),
    phone       VARCHAR(30),
    village     VARCHAR(120),
    milk_rate   NUMERIC(8, 2) CHECK (milk_rate >= 0),
    pay_every   VARCHAR(12)   CHECK (pay_every IN ('DELIVERY', 'WEEK', 'TEN_DAYS', 'FORTNIGHT', 'MONTH')),
    notes       TEXT,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID
);
CREATE UNIQUE INDEX ux_contact_name ON contact (farm_id, lower(name));

-- Milk grade rules (expected yield by lactation day + grade bands + quality checks). NULL = defaults.
ALTER TABLE farm ADD COLUMN milk_grading JSONB;

-- Milk and other produce. animal_id set = one cow's session; NULL = farm total / sale.
CREATE TABLE production_record (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id           BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    record_date       DATE          NOT NULL,
    session           VARCHAR(3)    NOT NULL CHECK (session IN ('AM', 'PM', 'DAY')),
    product           VARCHAR(8)    NOT NULL DEFAULT 'MILK' CHECK (product IN ('MILK', 'MANURE', 'OTHER')),
    animal_id         BIGINT        REFERENCES animal (id) ON DELETE CASCADE,
    quantity          NUMERIC(9, 2) CHECK (quantity >= 0),
    unit              VARCHAR(3)    NOT NULL DEFAULT 'L' CHECK (unit IN ('L', 'KG', 'NOS')),
    fat_pct           NUMERIC(4, 2) CHECK (fat_pct BETWEEN 0 AND 15),
    snf_pct           NUMERIC(4, 2) CHECK (snf_pct BETWEEN 0 AND 15),
    protein_pct       NUMERIC(4, 2) CHECK (protein_pct BETWEEN 0 AND 10),
    density           NUMERIC(5, 3) CHECK (density BETWEEN 0.9 AND 1.1),
    scc               INTEGER       CHECK (scc >= 0),
    withheld          BOOLEAN       NOT NULL DEFAULT FALSE,   -- milked during a withdrawal: discard, never sell
    sold_qty          NUMERIC(9, 2) CHECK (sold_qty >= 0),
    rate              NUMERIC(8, 2) CHECK (rate >= 0),
    income_amount     NUMERIC(12, 2) CHECK (income_amount >= 0),
    buyer_contact_id  BIGINT        REFERENCES contact (id),
    paid              BOOLEAN,
    notes             TEXT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        UUID,
    CONSTRAINT ck_prod_cow_session CHECK (animal_id IS NULL OR (session IN ('AM', 'PM') AND product = 'MILK')),
    CONSTRAINT ck_prod_sale CHECK (animal_id IS NULL OR (sold_qty IS NULL AND buyer_contact_id IS NULL))
);
CREATE UNIQUE INDEX ux_prod_animal ON production_record (animal_id, record_date, session) WHERE animal_id IS NOT NULL;
CREATE UNIQUE INDEX ux_prod_farm ON production_record (farm_id, record_date, session, product) WHERE animal_id IS NULL;
CREATE INDEX ix_prod_farm_date ON production_record (farm_id, record_date);
CREATE INDEX ix_prod_buyer ON production_record (buyer_contact_id);

-- Everything observed on a date. This round uses MEASUREMENT and HEALTH.
CREATE TABLE farm_event (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id                BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    animal_id              BIGINT        REFERENCES animal (id) ON DELETE CASCADE,
    batch_id               UUID,
    event_type             VARCHAR(12)   NOT NULL CHECK (event_type IN
                           ('MEASUREMENT', 'HEALTH', 'HEAT', 'CALF_FEEDING', 'BARN_READING', 'ATTENDANCE')),
    sub_type               VARCHAR(12),
    event_date             DATE          NOT NULL,
    event_time             TIME,
    weight_kg              NUMERIC(7, 2) CHECK (weight_kg > 0),
    height_cm              NUMERIC(6, 1) CHECK (height_cm > 0),
    bcs                    NUMERIC(3, 2) CHECK (bcs BETWEEN 1 AND 5),
    cost                   NUMERIC(12, 2) CHECK (cost >= 0),
    contact_id             BIGINT        REFERENCES contact (id),
    next_action_date       DATE,
    next_action            VARCHAR(160),
    next_action_done       BOOLEAN       NOT NULL DEFAULT FALSE,
    milk_withdrawal_until  TIMESTAMPTZ,
    meat_withdrawal_until  DATE,
    details                JSONB         NOT NULL DEFAULT '{}',
    notes                  TEXT,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by             UUID,
    CONSTRAINT ck_event_measure CHECK (event_type <> 'MEASUREMENT'
        OR (animal_id IS NOT NULL AND sub_type IN ('SCALE', 'TAPE', 'ESTIMATE')
            AND (weight_kg IS NOT NULL OR height_cm IS NOT NULL OR bcs IS NOT NULL))),
    CONSTRAINT ck_event_health CHECK (event_type <> 'HEALTH'
        OR (animal_id IS NOT NULL AND sub_type IN ('ILLNESS', 'TREATMENT', 'VACCINATION', 'DEWORMING', 'INJURY')))
);
CREATE INDEX ix_event_animal ON farm_event (farm_id, animal_id, event_type, event_date);
CREATE INDEX ix_event_farm_date ON farm_event (farm_id, event_type, event_date);
CREATE INDEX ix_event_follow_up ON farm_event (farm_id, next_action_date) WHERE next_action_date IS NOT NULL AND NOT next_action_done;
CREATE INDEX ix_event_milk_withdrawal ON farm_event (farm_id, milk_withdrawal_until) WHERE milk_withdrawal_until IS NOT NULL;
CREATE INDEX ix_event_batch ON farm_event (batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX ix_event_contact ON farm_event (contact_id);
