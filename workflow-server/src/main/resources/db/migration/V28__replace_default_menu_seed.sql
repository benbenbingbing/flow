-- 使用产品菜单种子替换旧的默认菜单。
-- 注意：这是全量菜单，不是增量补丁。

DELETE FROM sys_menu;

INSERT INTO sys_menu (`id`, `parent_id`, `menu_name`, `menu_type`, `icon`, `sort`, `path`, `component`, `perm`, `status`, `visible`, `keep_alive`, `breadcrumb`, `remark`, `deleted`, `create_by`, `create_time`, `update_by`, `update_time`, `is_frame`, `is_cache`, `query`, `entity_code`) VALUES
('100', '0', '首页', 'M', 'HomeFilled', 1, '/home', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-28 18:06:08', '0', '0', '', NULL),
('200', '300', '流程管理', 'M', 'Share', 2, '/process', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-11 21:13:51', '0', '0', '', NULL),
('2056241910275760129', '0', '操作日志', 'M', 'el-icon-document', 10, '/system/operation-log', NULL, NULL, '0', '0', '0', '1', NULL, 1, NULL, '2026-05-18 13:14:09', NULL, '2026-05-18 13:14:09', '0', '0', '', NULL),
('2059562934702460930', '0', '需求申请', 'C', 'Document', 2, '/entity/list/req01', 'entity/EntityDataList', '', '0', '0', '0', '1', NULL, 0, NULL, '2026-05-27 17:10:43', NULL, '2026-05-27 17:47:28', '0', '0', '', 'req01'),
('300', '0', '配置管理', 'M', 'Box', 3, '/entity', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-11 21:09:28', '0', '0', '', NULL),
('301', '300', '实体配置', 'M', 'Document', 1, '/entity', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-11 21:16:37', '0', '0', '', NULL),
('302', '0', '立项管理', 'M', 'Document', 2, '/entity/list/project_nitiation', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 23:59:09', '0', '0', '', NULL),
('400', '0', '系统管理', 'M', 'Setting', 4, '/system', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('401', '400', '用户管理', 'C', 'User', 1, '/system/user', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('402', '400', '角色管理', 'C', 'UserFilled', 2, '/system/role', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('403', '400', '用户组管理', 'C', 'FolderOpened', 3, '/system/group', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('404', '400', '组织部门管理', 'C', 'OfficeBuilding', 4, '/system/org', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('405', '400', '菜单管理', 'C', 'Menu', 5, '/system/menu', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-08 18:13:08', NULL, '2026-05-08 18:13:08', '0', '0', '', NULL),
('custom_form_guide', 'dev_guide_dir', '自定义表单组件', 'C', 'Document', 3, '/system/custom-form-guide', '/views/system/CustomFormGuide.vue', 'system:dev:list', '0', '0', '0', '1', NULL, 0, NULL, '2026-05-15 10:53:15', NULL, '2026-05-15 10:53:15', '0', '0', '', NULL),
('custom_list_guide', 'dev_guide_dir', '自定义列表组件', 'C', 'Document', 2, '/system/custom-list-guide', '/views/system/CustomListGuide.vue', 'system:dev:list', '0', '0', '0', '1', NULL, 0, NULL, '2026-05-15 10:53:15', NULL, '2026-05-15 10:53:15', '0', '0', '', NULL),
('dev_guide_dir', '400', '定制开发', 'M', 'Document', 99, '/dev', NULL, NULL, '0', '0', '0', '1', NULL, 0, NULL, '2026-05-09 15:20:31', NULL, '2026-05-09 15:20:31', '0', '0', '', NULL),
('dev_guide_list', 'dev_guide_dir', '列表字段扩展', 'C', 'Document', 1, '/system/dev-guide', '/views/system/DevGuide.vue', 'system:dev:list', '0', '0', '0', '1', NULL, 0, NULL, '2026-05-09 15:20:31', NULL, '2026-05-09 15:20:31', '0', '0', '', NULL),
('dict_mgmt', '400', '字典设置', 'C', 'Notebook', 6, '/system/dict', '/views/system/Dict.vue', 'system:dict:list', '0', '0', '0', '1', NULL, 0, NULL, '2026-05-11 22:29:49', NULL, '2026-05-28 18:16:10', '0', '0', '', NULL);
