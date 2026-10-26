-- Log feeding "Group" now means a feeding group (calculated daily, same as the ration planner), not a herd pen.
ALTER TABLE item_txn ADD COLUMN feed_group VARCHAR(20);
ALTER TABLE item_txn ADD CONSTRAINT item_txn_feed_group_check
    CHECK (feed_group IN ('FRESH', 'EARLY', 'MID', 'LATE', 'CLOSE_UP', 'STEAM_UP', 'DRY', 'HEIFER', 'CALF', 'BULL'));
