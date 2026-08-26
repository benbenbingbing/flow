-- 跨实体表单/列表“关联内容”设计态配置。
CREATE TABLE ui_view_composition (
    id VARCHAR(64) NOT NULL,
    owner_type VARCHAR(20) NOT NULL COMMENT 'FORM/LIST',
    owner_id VARCHAR(64) NOT NULL COMMENT '表单或列表配置ID',
    composition_key VARCHAR(100) NOT NULL COMMENT '宿主内稳定业务标识',
    anchor_type VARCHAR(32) NOT NULL DEFAULT 'OWNER' COMMENT 'OWNER/FORM_NODE/PAGE_SECTION/ROW_EXPAND/TOOLBAR_ACTION/ROW_ACTION',
    anchor_key VARCHAR(160) NULL COMMENT '非OWNER挂载点的稳定标识',
    config_document LONGTEXT NOT NULL COMMENT '经白名单校验的关联内容配置JSON',
    order_key BIGINT NOT NULL DEFAULT 1000 COMMENT '同一挂载点内的稳定排序键',
    revision INT NOT NULL DEFAULT 1 COMMENT '乐观锁修订号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    active_composition_key VARCHAR(100) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 THEN composition_key ELSE NULL END
    ) STORED COMMENT '仅活动记录参与宿主内编码唯一约束',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ui_view_composition_active_key (
        owner_type, owner_id, active_composition_key
    ),
    KEY idx_ui_view_composition_owner_order (
        owner_type, owner_id, deleted, order_key
    ),
    CONSTRAINT chk_ui_view_composition_owner
        CHECK (owner_type IN ('FORM', 'LIST')),
    CONSTRAINT chk_ui_view_composition_revision
        CHECK (revision >= 1),
    CONSTRAINT chk_ui_view_composition_deleted
        CHECK (deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单列表关联内容配置';
