ALTER TABLE `process_task`
    ADD COLUMN `action_label` varchar(200) DEFAULT NULL COMMENT '操作显示文本，如"同意，需要会签"' AFTER `action`;
