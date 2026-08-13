ALTER TABLE `entity_list_config`
  ALTER COLUMN `published_version` SET DEFAULT 0;

UPDATE `entity_list_config` c
LEFT JOIN `ui_config_release` r
  ON r.`id` = c.`active_release_id`
 AND r.`config_type` = 'LIST'
 AND r.`config_id` = c.`id`
 AND r.`status` = 'ACTIVE'
SET c.`active_release_id` = CASE
      WHEN r.`id` IS NULL THEN NULL
      ELSE r.`id`
    END,
    c.`published_version` = COALESCE(r.`version`, 0),
    c.`draft_hash` = CASE
      WHEN r.`id` IS NULL THEN NULL
      ELSE c.`draft_hash`
    END;
