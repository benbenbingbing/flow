ALTER TABLE entity_definition
    ADD COLUMN team_visibility_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许数据参与团队查看记录',
    ADD COLUMN team_visibility_level VARCHAR(30) NOT NULL DEFAULT 'ADDITIVE' COMMENT '参与团队权限级别：ADDITIVE/OVERRIDE_SCOPE/ABSOLUTE';

ALTER TABLE entity_publish_history
    ADD COLUMN team_visibility_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '发布时是否允许数据参与团队查看记录',
    ADD COLUMN team_visibility_level VARCHAR(30) NOT NULL DEFAULT 'ADDITIVE' COMMENT '发布时参与团队权限级别';
