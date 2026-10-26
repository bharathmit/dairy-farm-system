-- Ration rules move from 5 animal categories to 10 feeding groups (docs/api-feed.md, "Feeding groups").
-- Existing rules are copied so current settings carry over: MILKING → FRESH, EARLY, MID, LATE;
-- DRY → CLOSE_UP, STEAM_UP (DRY stays); HEIFER, CALF, BULL stay as they are.

ALTER TABLE ration_rule DROP CONSTRAINT IF EXISTS ration_rule_category_check;

INSERT INTO ration_rule (farm_id, category, effective_from, params, created_at, updated_at, created_by)
SELECT r.farm_id, g.grp, r.effective_from, r.params, r.created_at, r.updated_at, r.created_by
FROM ration_rule r
JOIN (VALUES ('MILKING', 'FRESH'), ('MILKING', 'EARLY'), ('MILKING', 'MID'), ('MILKING', 'LATE'),
             ('DRY', 'CLOSE_UP'), ('DRY', 'STEAM_UP')) AS g (src, grp) ON g.src = r.category
ON CONFLICT (farm_id, category, effective_from) DO NOTHING;

DELETE FROM ration_rule WHERE category = 'MILKING';

ALTER TABLE ration_rule ADD CONSTRAINT ration_rule_category_check
    CHECK (category IN ('FRESH', 'EARLY', 'MID', 'LATE', 'CLOSE_UP', 'STEAM_UP', 'DRY', 'HEIFER', 'CALF', 'BULL'));
