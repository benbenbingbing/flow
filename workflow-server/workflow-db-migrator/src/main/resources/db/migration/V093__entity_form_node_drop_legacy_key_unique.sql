-- 节点编码唯一性已由 (form_id, active_node_key) 唯一键保证，active_node_key 仅活动节点非空。
-- 旧唯一键 (form_id, node_key, deleted) 只允许同一编码保留一条删除记录，
-- 同一子表单/字段删除后重新添加并再次删除时，软删除更新会触发唯一冲突。
-- 这里移除旧唯一键并保留普通索引，维持按表单加节点编码的查询能力。
ALTER TABLE `entity_form_node`
    DROP KEY `uk_entity_form_node_key`,
    ADD KEY `idx_entity_form_node_form_key` (`form_id`, `node_key`);
