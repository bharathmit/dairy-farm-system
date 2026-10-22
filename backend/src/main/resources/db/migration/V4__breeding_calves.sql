-- Canvas 3: breeding & calves. Heats and calf feeding are farm_event rows (HEAT, CALF_FEEDING).

ALTER TABLE animal ADD COLUMN lactation_number INT NOT NULL DEFAULT 0 CHECK (lactation_number BETWEEN 0 AND 30);

-- Gestation, heat cycle, dry-off, keep-or-sell and calf rearing rules. NULL = defaults.
ALTER TABLE farm ADD COLUMN breeding_settings JSONB;

-- Anything stocked or owned. Used for SEMEN in this round (feed, medicine, equipment later).
CREATE TABLE item (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id              BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    item_type            VARCHAR(10)    NOT NULL CHECK (item_type IN ('FEED', 'SEMEN', 'MEDICINE', 'EQUIPMENT', 'OTHER')),
    name                 VARCHAR(120)   NOT NULL,
    category             VARCHAR(30),
    unit                 VARCHAR(6)     NOT NULL CHECK (unit IN ('KG', 'L', 'STRAW', 'DOSE', 'NOS')),
    cost_per_unit        NUMERIC(12, 2) CHECK (cost_per_unit >= 0),
    reorder_level        NUMERIC(12, 3) CHECK (reorder_level >= 0),
    lead_time_days       INT            CHECK (lead_time_days >= 0),
    supplier_contact_id  BIGINT         REFERENCES contact (id),
    batch_no             VARCHAR(60),
    expiry_date          DATE,
    details              JSONB          NOT NULL DEFAULT '{}',   -- SEMEN: bullCode, breed, sexed
    notes                TEXT,
    active               BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID
);
CREATE INDEX ix_item_farm_type ON item (farm_id, item_type);
CREATE INDEX ix_item_supplier ON item (supplier_contact_id);

-- Stock movements. Stock = PURCHASE + PRODUCE − USAGE + ADJUSTMENT (adjustment quantity is signed).
CREATE TABLE item_txn (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id              BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    item_id              BIGINT         NOT NULL REFERENCES item (id) ON DELETE CASCADE,
    txn_type             VARCHAR(10)    NOT NULL CHECK (txn_type IN ('PURCHASE', 'USAGE', 'ADJUSTMENT', 'SERVICE', 'PRODUCE')),
    txn_date             DATE           NOT NULL,
    txn_time             TIME,
    quantity             NUMERIC(12, 3) NOT NULL,
    cost                 NUMERIC(12, 2) CHECK (cost >= 0),
    supplier_contact_id  BIGINT         REFERENCES contact (id),
    animal_id            BIGINT         REFERENCES animal (id) ON DELETE SET NULL,
    group_id             BIGINT         REFERENCES animal_group (id) ON DELETE SET NULL,
    source_type          VARCHAR(20),
    source_id            BIGINT,
    notes                TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID,
    CONSTRAINT ck_txn_sign CHECK (txn_type = 'ADJUSTMENT' OR quantity > 0)
);
CREATE INDEX ix_item_txn_item ON item_txn (item_id, txn_date);
CREATE INDEX ix_item_txn_source ON item_txn (source_type, source_id);

-- One row per service → pregnancy → calving cycle. A calving with no recorded service has method UNKNOWN.
CREATE TABLE breeding_record (
    id                           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id                      BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    dam_id                       BIGINT         NOT NULL REFERENCES animal (id) ON DELETE CASCADE,
    lactation_number             INT            NOT NULL DEFAULT 0,   -- the dam's lactation when served
    heat_event_id                BIGINT         REFERENCES farm_event (id) ON DELETE SET NULL,
    service_date                 DATE,
    service_time                 TIME,
    method                       VARCHAR(8)     NOT NULL CHECK (method IN ('NATURAL', 'AI', 'ET', 'UNKNOWN')),
    semen_item_id                BIGINT         REFERENCES item (id),
    straws                       INT            CHECK (straws BETWEEN 1 AND 5),
    sire_id                      BIGINT         REFERENCES animal (id) ON DELETE SET NULL,
    sire_tag                     VARCHAR(60),
    bull_exposure_start          DATE,
    bull_exposure_end            DATE,
    technician_contact_id        BIGINT         REFERENCES contact (id),
    technician_fee               NUMERIC(12, 2) CHECK (technician_fee >= 0),
    bull_owner_contact_id        BIGINT         REFERENCES contact (id),
    bull_fee                     NUMERIC(12, 2) CHECK (bull_fee >= 0),
    semen_cost                   NUMERIC(12, 2) CHECK (semen_cost >= 0),
    fee_paid                     BOOLEAN        NOT NULL DEFAULT FALSE,
    expected_due_date            DATE,
    pregnancy_status             VARCHAR(12)    NOT NULL DEFAULT 'UNKNOWN'
                                 CHECK (pregnancy_status IN ('UNKNOWN', 'PREGNANT', 'NOT_PREGNANT')),
    preg_check_date              DATE,
    preg_check_method            VARCHAR(10)    CHECK (preg_check_method IN ('ULTRASOUND', 'PALPATION', 'BLOOD')),
    preg_check_cost              NUMERIC(12, 2) CHECK (preg_check_cost >= 0),
    preg_checked_by_contact_id   BIGINT         REFERENCES contact (id),
    calving_date                 DATE,
    calving_time                 TIME,
    calving_outcome              VARCHAR(10)    CHECK (calving_outcome IN ('LIVE', 'STILLBORN', 'ABORTED')),
    calving_ease                 VARCHAR(9)     CHECK (calving_ease IN ('EASY', 'ASSISTED', 'HARD', 'VET')),
    calves                       JSONB          NOT NULL DEFAULT '[]',   -- [{animalId, sex, birthWeightKg}]
    colostrum_given_at           TIME,
    colostrum_litres             NUMERIC(4, 1)  CHECK (colostrum_litres >= 0),
    retained_placenta            BOOLEAN        NOT NULL DEFAULT FALSE,
    notes                        TEXT,
    created_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by                   UUID,
    CONSTRAINT ck_breeding_dates CHECK (service_date IS NOT NULL OR calving_date IS NOT NULL),
    CONSTRAINT ck_breeding_method CHECK (method = 'UNKNOWN' OR service_date IS NOT NULL),
    CONSTRAINT ck_breeding_calving CHECK (calving_date IS NULL OR service_date IS NULL OR calving_date >= service_date)
);
CREATE INDEX ix_breeding_dam ON breeding_record (farm_id, dam_id, service_date);
CREATE INDEX ix_breeding_calving ON breeding_record (farm_id, calving_date) WHERE calving_date IS NOT NULL;
CREATE INDEX ix_breeding_semen ON breeding_record (semen_item_id);
CREATE INDEX ix_breeding_heat ON breeding_record (heat_event_id);
CREATE INDEX ix_breeding_sire ON breeding_record (sire_id);
CREATE INDEX ix_breeding_contacts ON breeding_record (technician_contact_id, bull_owner_contact_id, preg_checked_by_contact_id);

-- Calf feeding: one row per calf per day.
CREATE UNIQUE INDEX ux_event_calf_feeding ON farm_event (animal_id, event_date) WHERE event_type = 'CALF_FEEDING';
