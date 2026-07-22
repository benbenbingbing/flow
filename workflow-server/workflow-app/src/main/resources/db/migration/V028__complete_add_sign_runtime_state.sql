ALTER TABLE `process_task_add_sign`
  ADD COLUMN `source_completed` tinyint NOT NULL DEFAULT 0 COMMENT '原任务是否已提交' AFTER `engine_execution_id`,
  ADD COLUMN `source_action` varchar(100) DEFAULT NULL COMMENT '原任务提交动作' AFTER `source_completed`,
  ADD COLUMN `source_action_label` varchar(200) DEFAULT NULL COMMENT '原任务动作名称' AFTER `source_action`,
  ADD COLUMN `source_comment` varchar(1000) DEFAULT NULL COMMENT '原任务审批意见' AFTER `source_action_label`,
  ADD COLUMN `source_form_data` longtext DEFAULT NULL COMMENT '原任务表单数据JSON' AFTER `source_comment`;
