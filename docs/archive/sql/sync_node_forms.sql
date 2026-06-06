-- 同步 BPMN XML 中的节点表单配置到 process_node_form 表
-- 手动为 projectinit 流程插入已知的节点表单绑定

-- 先清空已有数据（避免重复）
DELETE FROM workflow.process_node_form WHERE process_config_id = '28';

-- 插入 projectinit 流程的节点表单绑定
INSERT INTO workflow.process_node_form (id, process_config_id, node_id, node_name, form_id, is_readonly, create_time, update_time)
VALUES
  ('28_node_admin', '28', 'Activity_0c9s28z', '管理员新增', '2049423744157384706', 0, NOW(), NOW()),
  ('28_node_second', '28', 'Activity_1stkhyf', '第二个节点', '2049424188225126402', 0, NOW(), NOW());
