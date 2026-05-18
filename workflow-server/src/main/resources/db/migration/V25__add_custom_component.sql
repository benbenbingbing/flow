-- 列表配置增加自定义组件字段
ALTER TABLE entity_list_config ADD COLUMN custom_component VARCHAR(100) NULL COMMENT '自定义列表组件注册名';

-- 表单定义增加自定义组件字段
ALTER TABLE entity_form ADD COLUMN custom_component VARCHAR(100) NULL COMMENT '自定义表单组件注册名';
