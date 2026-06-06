-- ========================================================-- V19: 扩展实体列表字段配置表，支持自定义数据源和渲染组件
-- 说明: 为列表字段增加数据源类型、数据源配置、渲染组件、格式化表达式字段
-- ========================================================

ALTER TABLE `entity_list_field`
    ADD COLUMN `data_source_type` VARCHAR(32) DEFAULT 'ENTITY_FIELD' COMMENT '数据源类型：ENTITY_FIELD(实体字段)/REFERENCE(关联查询)/AGGREGATE(聚合统计)/CUSTOM_PROVIDER(自定义处理器)',
    ADD COLUMN `data_source_config` TEXT COMMENT '数据源配置JSON',
    ADD COLUMN `render_component` VARCHAR(64) COMMENT '前端渲染组件名',
    ADD COLUMN `formatter` VARCHAR(255) COMMENT '简单格式化表达式（如 yyyy-MM-dd、#0.00）';
