-- Module 1: people and farms.
-- app_user.id is the Supabase Auth user id (JWT "sub").

CREATE TABLE app_user (
    id           UUID PRIMARY KEY,
    full_name    VARCHAR(120),
    email        VARCHAR(254),
    phone        VARCHAR(30),
    preferences  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_app_user_email ON app_user (lower(email));

CREATE TABLE farm (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    village     VARCHAR(120),
    district    VARCHAR(120),
    state       VARCHAR(120),
    country     VARCHAR(120),
    latitude    NUMERIC(9, 6),
    longitude   NUMERIC(9, 6),
    settings    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID
);

-- Owners and workers. A worker can exist without a login (user_id null).
CREATE TABLE farm_member (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id        BIGINT       NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    user_id        UUID         REFERENCES app_user (id),
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(254),
    phone          VARCHAR(30),
    date_of_birth  DATE,
    sex            VARCHAR(10)  CHECK (sex IN ('FEMALE', 'MALE')),
    role           VARCHAR(10)  NOT NULL CHECK (role IN ('OWNER', 'WORKER')),
    wage_type      VARCHAR(10)  NOT NULL DEFAULT 'NONE' CHECK (wage_type IN ('NONE', 'DAILY', 'MONTHLY')),
    wage_rate      NUMERIC(12, 2) CHECK (wage_rate >= 0),
    joined_on      DATE,
    left_on        DATE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Login: NONE = no sign-in, PASSWORD = owner set the password, INVITE = emailed invite link.
    login_type     VARCHAR(10)  NOT NULL DEFAULT 'NONE' CHECK (login_type IN ('NONE', 'PASSWORD', 'INVITE', 'SELF')),
    invited_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by     UUID
);
CREATE UNIQUE INDEX ux_farm_member_user  ON farm_member (farm_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX ux_farm_member_email ON farm_member (farm_id, lower(email)) WHERE email IS NOT NULL;
CREATE INDEX ix_farm_member_user ON farm_member (user_id);
CREATE INDEX ix_farm_member_pending_email ON farm_member (lower(email)) WHERE user_id IS NULL AND email IS NOT NULL;
