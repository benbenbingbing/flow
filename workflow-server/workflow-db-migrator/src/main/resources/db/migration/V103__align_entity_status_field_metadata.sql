-- 主表状态列从建表起固定为 varchar(50)，旧实体字段目录误记为 varchar(20)。
-- 只修正系统 status 的展示元数据；不修改已存在的业务列或历史发布快照。
UPDATE entity_field
SET db_type = 'varchar(50)', field_length = 50
WHERE is_system = 1
  AND field_code = 'status'
  AND (field_length <> 50 OR db_type <> 'varchar(50)');
