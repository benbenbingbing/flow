-- 实体列表配置增加工具栏按钮和操作列按钮配置
ALTER TABLE `entity_list_config`
  ADD COLUMN `toolbar_config` json DEFAULT NULL COMMENT '工具栏按钮配置JSON',
  ADD COLUMN `row_action_config` json DEFAULT NULL COMMENT '操作列按钮配置JSON';
