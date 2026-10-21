-- Module 3 and 4: groups and animals.

CREATE TABLE animal_group (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id            BIGINT       NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    name               VARCHAR(80)  NOT NULL,
    species            VARCHAR(10)  NOT NULL CHECK (species IN ('COW', 'BUFFALO', 'GOAT', 'SHEEP', 'PIG', 'POULTRY')),
    location           VARCHAR(120),
    target_outcome     VARCHAR(12)  NOT NULL DEFAULT 'MAINTENANCE'
                       CHECK (target_outcome IN ('GROWTH', 'MAINTENANCE', 'RECOVERY', 'GESTATION', 'OTHER')),
    manager_member_id  BIGINT       REFERENCES farm_member (id) ON DELETE SET NULL,
    colour             VARCHAR(7)   CHECK (colour ~ '^#[0-9A-Fa-f]{6}$'),
    notes              TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by         UUID
);
CREATE UNIQUE INDEX ux_animal_group_name ON animal_group (farm_id, lower(name));

CREATE TABLE animal (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id            BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    tag                VARCHAR(40)   NOT NULL,
    name               VARCHAR(80),
    national_id        VARCHAR(40),
    species            VARCHAR(10)   NOT NULL CHECK (species IN ('COW', 'BUFFALO', 'GOAT', 'SHEEP', 'PIG', 'POULTRY')),
    breed              VARCHAR(60),
    sex                VARCHAR(10)   NOT NULL CHECK (sex IN ('FEMALE', 'MALE')),
    date_of_birth      DATE,
    colour             VARCHAR(60),
    birth_weight_kg    NUMERIC(7, 2) CHECK (birth_weight_kg > 0),
    category           VARCHAR(10)   NOT NULL CHECK (category IN ('CALF', 'HEIFER', 'MILKING', 'DRY', 'BULL', 'OTHER')),
    status             VARCHAR(12)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SOLD', 'DEAD', 'TRANSFERRED')),
    group_id           BIGINT        REFERENCES animal_group (id) ON DELETE SET NULL,
    dam_id             BIGINT        REFERENCES animal (id) ON DELETE SET NULL,
    sire_id            BIGINT        REFERENCES animal (id) ON DELETE SET NULL,
    dam_tag            VARCHAR(40),
    sire_tag           VARCHAR(60),
    acquisition_type   VARCHAR(10)   NOT NULL CHECK (acquisition_type IN ('BORN', 'PURCHASED')),
    acquisition_date   DATE,
    purchase_price     NUMERIC(12, 2) CHECK (purchase_price >= 0),
    exit_date          DATE,
    exit_reason        VARCHAR(80),
    exit_price         NUMERIC(12, 2) CHECK (exit_price >= 0),
    exit_party         VARCHAR(120),
    photo_paths        TEXT[]        NOT NULL DEFAULT '{}' CHECK (cardinality(photo_paths) <= 3),
    -- Opening values from the animal form. Later modules (breeding, measurements) keep them current.
    last_calving_date  DATE,
    current_weight_kg  NUMERIC(7, 2) CHECK (current_weight_kg > 0),
    notes              TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID,
    CONSTRAINT ck_animal_born_dob CHECK (acquisition_type <> 'BORN' OR date_of_birth IS NOT NULL),
    CONSTRAINT ck_animal_exit CHECK (status = 'ACTIVE' OR exit_date IS NOT NULL)
);
CREATE UNIQUE INDEX ux_animal_tag ON animal (farm_id, lower(tag));
CREATE INDEX ix_animal_farm_status ON animal (farm_id, status, category);
CREATE INDEX ix_animal_group ON animal (group_id);
CREATE INDEX ix_animal_dam ON animal (dam_id);
CREATE INDEX ix_animal_sire ON animal (sire_id);
