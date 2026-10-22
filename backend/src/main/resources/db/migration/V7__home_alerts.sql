-- Canvas 6: alerts and reminders, alert rules, health schedules, daily checklist, cull advisor.

CREATE TABLE alert (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id        BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    source         VARCHAR(11)   NOT NULL CHECK (source IN ('RULE', 'REMINDER', 'PROCUREMENT')),
    rule_code      VARCHAR(32),
    rule_key       VARCHAR(160),                -- identity of a rule hit; released (NULL) once the condition clears
    category       VARCHAR(12)   NOT NULL CHECK (category IN ('REPRODUCTION', 'CALF', 'HEALTH', 'MILK', 'FEED', 'FINANCE_OPS')),
    severity       VARCHAR(6)    NOT NULL CHECK (severity IN ('HIGH', 'MEDIUM', 'INFO')),
    title          VARCHAR(160)  NOT NULL,
    detail         TEXT,
    animal_id      BIGINT        REFERENCES animal (id) ON DELETE CASCADE,
    related_type   VARCHAR(24),
    related_id     BIGINT,
    due_date       DATE          NOT NULL,
    due_time       TIME,
    worker_visible BOOLEAN       NOT NULL DEFAULT TRUE,
    status         VARCHAR(9)    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DONE', 'DISMISSED')),
    snoozed_until  TIMESTAMPTZ,
    done_by        BIGINT        REFERENCES farm_member (id) ON DELETE SET NULL,
    done_at        TIMESTAMPTZ,
    auto_closed    BOOLEAN       NOT NULL DEFAULT FALSE,
    repeat         VARCHAR(7)    NOT NULL DEFAULT 'NONE' CHECK (repeat IN ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY')),
    reminder_type  VARCHAR(6)    CHECK (reminder_type IN ('HEALTH', 'BREED', 'FEED', 'VET', 'OTHER')),
    details        JSONB         NOT NULL DEFAULT '{}',
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID
);
CREATE UNIQUE INDEX ux_alert_rule_key ON alert (farm_id, rule_key) WHERE rule_key IS NOT NULL;
CREATE INDEX ix_alert_open ON alert (farm_id, status, due_date);
CREATE INDEX ix_alert_animal ON alert (animal_id);

ALTER TABLE farm ADD COLUMN alert_rules JSONB;
ALTER TABLE farm ADD COLUMN alerts_refreshed_at TIMESTAMPTZ;
ALTER TABLE farm ADD COLUMN schedules_seeded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE health_schedule (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id            BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    type               VARCHAR(11)   NOT NULL CHECK (type IN ('DEWORMING', 'VACCINATION')),
    name               VARCHAR(120)  NOT NULL,
    applies_to         VARCHAR(14)   NOT NULL CHECK (applies_to IN ('CALVES_HEIFERS', 'ALL', 'MILKING')),
    medicine           VARCHAR(120),
    steps              JSONB         NOT NULL DEFAULT '[]',
    alert_days_before  INT           NOT NULL DEFAULT 2 CHECK (alert_days_before BETWEEN 0 AND 60),
    worker_visible     BOOLEAN       NOT NULL DEFAULT TRUE,
    active             BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by         UUID
);
CREATE INDEX ix_health_schedule_farm ON health_schedule (farm_id);

CREATE TABLE checklist_task (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id         BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    name            VARCHAR(120)  NOT NULL,
    repeat_daily    BOOLEAN       NOT NULL DEFAULT TRUE,
    worker_visible  BOOLEAN       NOT NULL DEFAULT TRUE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order      INT           NOT NULL DEFAULT 0,
    task_date       DATE,                                  -- one-off task (repeat_daily = false)
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by      UUID
);
CREATE INDEX ix_checklist_task_farm ON checklist_task (farm_id);

CREATE TABLE checklist_tick (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    farm_id     BIGINT        NOT NULL REFERENCES farm (id) ON DELETE CASCADE,
    task_key    VARCHAR(40)   NOT NULL,
    tick_date   DATE          NOT NULL,
    member_id   BIGINT        REFERENCES farm_member (id) ON DELETE SET NULL,
    ticked_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_checklist_tick ON checklist_tick (farm_id, task_key, tick_date);

ALTER TABLE animal ADD COLUMN cull_keep_until DATE;
