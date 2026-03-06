-- 添加逻辑删除字段到各表

-- 1. 流程定义配置表
ALTER TABLE process_definition_config 
ADD COLUMN deleted INT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除';

CREATE INDEX idx_deleted ON process_definition_config(deleted);

-- 2. 流程版本历史表
ALTER TABLE process_version_history 
ADD COLUMN deleted INT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除';

CREATE INDEX idx_version_deleted ON process_version_history(deleted);

-- 3. 流程动作表
ALTER TABLE flow_action 
ADD COLUMN deleted INT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除';

CREATE INDEX idx_action_deleted ON flow_action(deleted);
