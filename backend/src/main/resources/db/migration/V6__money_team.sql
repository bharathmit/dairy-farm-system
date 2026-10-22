-- Module 9–10 (Canvas 5): finance entries, attendance, equipment services, procurement notes.

CREATE TABLE finance_entry (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id           BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    direction         VARCHAR(7)     NOT NULL CHECK (direction IN ('INCOME', 'EXPENSE')),
    category          VARCHAR(16)    NOT NULL CHECK (category IN
                      ('MILK_SALES', 'ANIMAL_SALES', 'MANURE_SALES', 'OTHER_INCOME',
                       'FEED', 'LABOUR', 'VET', 'BREEDING', 'SEMEN', 'EQUIPMENT', 'UTILITIES', 'TRANSPORT',
                       'ANIMAL_PURCHASE', 'OTHER_EXPENSE')),
    amount            NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    entry_date        DATE           NOT NULL,
    entry_time        TIME,
    status            VARCHAR(7)     NOT NULL DEFAULT 'PAID' CHECK (status IN ('PAID', 'PENDING')),
    payment_method    VARCHAR(14)    CHECK (payment_method IN ('CASH', 'UPI', 'BANK_TRANSFER', 'CARD', 'CHEQUE', 'OTHER')),
    counterparty      VARCHAR(160),
    contact_id        BIGINT         REFERENCES contact (id) ON DELETE SET NULL,
    member_id         BIGINT         REFERENCES farm_member (id) ON DELETE SET NULL,
    period_from       DATE,
    period_to         DATE,
    advance           BOOLEAN        NOT NULL DEFAULT FALSE,   -- wage advance, deducted from the next wage due
    advance_deducted  NUMERIC(12, 2) CHECK (advance_deducted >= 0),
    alloc_kind        VARCHAR(6)     NOT NULL DEFAULT 'FARM' CHECK (alloc_kind IN ('FARM', 'GROUP', 'ANIMAL')),
    group_id          BIGINT         REFERENCES animal_group (id) ON DELETE SET NULL,
    animal_id         BIGINT         REFERENCES animal (id) ON DELETE SET NULL,
    quantity          NUMERIC(12, 3),
    unit              VARCHAR(6),
    source_type       VARCHAR(12)    CHECK (source_type IN ('MILK_SALE', 'ITEM_TXN', 'HEALTH', 'BREEDING', 'ANIMAL')),
    source_id         BIGINT,
    source_part       VARCHAR(12)    NOT NULL DEFAULT '',
    description       VARCHAR(240),                            -- what the source was, e.g. "Groundnut cake 50 kg"
    auto              BOOLEAN        NOT NULL DEFAULT FALSE,
    notes             TEXT,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by        UUID,
    CONSTRAINT ck_fin_direction CHECK ((direction = 'INCOME') = (category IN ('MILK_SALES', 'ANIMAL_SALES', 'MANURE_SALES', 'OTHER_INCOME'))),
    CONSTRAINT ck_fin_auto CHECK (auto = (source_type IS NOT NULL))
);
CREATE UNIQUE INDEX ux_fin_source ON finance_entry (farm_id, source_type, source_id, source_part) WHERE source_type IS NOT NULL;
CREATE INDEX ix_fin_farm_date ON finance_entry (farm_id, entry_date);
CREATE INDEX ix_fin_animal ON finance_entry (animal_id) WHERE animal_id IS NOT NULL;
CREATE INDEX ix_fin_member ON finance_entry (member_id) WHERE member_id IS NOT NULL;
CREATE INDEX ix_fin_pending ON finance_entry (farm_id) WHERE status = 'PENDING';

CREATE TABLE attendance (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id     BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    member_id   BIGINT        NOT NULL REFERENCES farm_member (id) ON DELETE CASCADE,
    work_date   DATE          NOT NULL,
    status      VARCHAR(7)    NOT NULL CHECK (status IN ('PRESENT', 'HALF', 'ABSENT')),
    hours       NUMERIC(4, 1) CHECK (hours BETWEEN 0 AND 24),
    notes       TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID,
    UNIQUE (member_id, work_date)
);
CREATE INDEX ix_attendance_farm_date ON attendance (farm_id, work_date);

-- Equipment services are item_txn SERVICE rows.
ALTER TABLE item_txn
    ADD COLUMN work_done          VARCHAR(12) CHECK (work_done IN ('ROUTINE', 'LINER_CHANGE', 'REPAIR', 'PARTS', 'OTHER')),
    ADD COLUMN mechanic_name      VARCHAR(120),
    ADD COLUMN next_service_date  DATE;

CREATE TABLE procurement_note (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id              BIGINT         NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    item_id              BIGINT         REFERENCES item (id) ON DELETE SET NULL,
    title                VARCHAR(160)   NOT NULL,
    quantity             NUMERIC(12, 3) CHECK (quantity > 0),
    unit                 VARCHAR(6),
    expected_price       NUMERIC(12, 2) CHECK (expected_price >= 0),
    needed_by            DATE,
    supplier_contact_id  BIGINT         REFERENCES contact (id) ON DELETE SET NULL,
    status               VARCHAR(9)     NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'ORDERED', 'RECEIVED', 'CANCELLED')),
    ordered_on           DATE,
    received_on          DATE,
    paid_amount          NUMERIC(12, 2) CHECK (paid_amount >= 0),
    purchase_txn_id      BIGINT         REFERENCES item_txn (id) ON DELETE SET NULL,
    finance_entry_id     BIGINT         REFERENCES finance_entry (id) ON DELETE SET NULL,
    source               VARCHAR(9)     NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('MANUAL', 'LOW_STOCK', 'FEED_NEED')),
    notes                TEXT,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by           UUID
);
CREATE INDEX ix_procurement_farm ON procurement_note (farm_id, status);
CREATE INDEX ix_procurement_item ON procurement_note (item_id);
