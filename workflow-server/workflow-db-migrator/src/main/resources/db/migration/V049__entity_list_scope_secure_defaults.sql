ALTER TABLE `entity_list_config`
  ADD COLUMN `unbound_scope_policy` varchar(30) NOT NULL DEFAULT 'DENY_ALL'
    COMMENT '未绑定ALLOW规则时策略：DENY_ALL/PERSONAL/EXPLICIT_ALL' AFTER `data_scope_mode`,
  ADD COLUMN `scope_enforcement_mode` varchar(20) NOT NULL DEFAULT 'ENFORCE'
    COMMENT '安全默认值执行阶段：OBSERVE/ENFORCE' AFTER `unbound_scope_policy`,
  ADD COLUMN `scope_default_confirmed` tinyint NOT NULL DEFAULT '0'
    COMMENT 'EXPLICIT_ALL是否已由管理员显式确认' AFTER `scope_enforcement_mode`,
  ADD COLUMN `scope_default_confirmed_by` varchar(64) DEFAULT NULL
    COMMENT '全量可见确认人' AFTER `scope_default_confirmed`,
  ADD COLUMN `scope_default_confirmed_at` datetime DEFAULT NULL
    COMMENT '全量可见确认时间' AFTER `scope_default_confirmed_by`,
  ADD COLUMN `scope_default_confirmation_note` varchar(500) DEFAULT NULL
    COMMENT '全量可见确认原因' AFTER `scope_default_confirmed_at`;

-- 存量列表先保持原有“未绑定即全部”行为，但进入观察期并要求管理员确认；
-- 迁移后新建列表使用列默认值 DENY_ALL + ENFORCE，默认拒绝访问。
UPDATE `entity_list_config`
SET `unbound_scope_policy` = 'EXPLICIT_ALL',
    `scope_enforcement_mode` = 'OBSERVE',
    `scope_default_confirmed` = 0,
    `scope_default_confirmed_by` = NULL,
    `scope_default_confirmed_at` = NULL,
    `scope_default_confirmation_note` = NULL
WHERE `deleted` = 0;
