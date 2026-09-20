-- 上下文绑定仅是未被消费的列表元数据，退役入口时一并删除草稿列及存量值。
-- 列表字段、固定条件、可信关联过滤和 Embed 自身的 context_bindings_json 不受影响。
-- 所有草稿修订号递增，防止迁移前打开的编辑页继续覆盖迁移后的配置。
UPDATE `entity_list_config`
SET `revision` = `revision` + 1,
    `draft_hash` = NULL,
    `update_time` = CURRENT_TIMESTAMP;

ALTER TABLE `entity_list_config` DROP COLUMN `context_binding_config`;

-- 历史发布快照参与内容哈希和钉版依赖校验，不能原地改写。
-- 应用读取旧快照时忽略 contextBindingConfig，后续保存和发布不再生成该字段。
