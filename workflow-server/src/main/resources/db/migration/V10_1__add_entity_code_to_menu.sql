-- 菜单表添加实体编码字段
ALTER TABLE sys_menu
ADD COLUMN entity_code VARCHAR(100) COMMENT '关联实体编码，当菜单类型为C且配置了此字段时，点击菜单将跳转到对应实体的数据列表';

-- 添加索引
CREATE INDEX idx_entity_code ON sys_menu(entity_code);
