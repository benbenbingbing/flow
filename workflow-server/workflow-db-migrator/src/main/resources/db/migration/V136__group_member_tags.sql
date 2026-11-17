-- 组内成员标签；流程引用稳定标签 ID，空标签条件保留全组语义。
CREATE TABLE sys_group_tag (
    id VARCHAR(64) NOT NULL COMMENT '标签ID：供流程配置稳定引用，改名不改变审批条件。',
    group_id VARCHAR(64) NOT NULL COMMENT '所属用户组：标签只在该组内有效。',
    tag_name VARCHAR(50) NOT NULL COMMENT '标签名称：供组内成员管理和审批范围选择展示。',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间：用于标签目录的稳定排序。',
    PRIMARY KEY (id),
    CONSTRAINT uk_group_tag_name UNIQUE (group_id, tag_name),
    CONSTRAINT fk_group_tag_group FOREIGN KEY (group_id) REFERENCES sys_group(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组内成员标签目录';
CREATE TABLE sys_user_group_tag (
    user_group_id VARCHAR(64) NOT NULL COMMENT '组成员关系ID：离组时清理绑定，普通成员保存保留标签。',
    tag_id VARCHAR(64) NOT NULL COMMENT '标签ID：同一组内多标签按或匹配，组间合并去重。',
    PRIMARY KEY (user_group_id, tag_id),
    CONSTRAINT fk_member_tag_member FOREIGN KEY (user_group_id) REFERENCES sys_user_group(id),
    CONSTRAINT fk_member_tag_tag FOREIGN KEY (tag_id) REFERENCES sys_group_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组成员标签绑定';
CREATE INDEX idx_member_tag_lookup ON sys_user_group_tag(tag_id, user_group_id);
-- 受控目录必须注册并启用，解析器实现存在本身不能绕过目录治理。
INSERT INTO process_person_resolver_definition
(id, resolver_code, display_name, description, bean_name, implementation_version, contract_version,
 supported_usages_document, extra_param_schema_document, dynamic_extra_params, enabled, revision, create_time, update_time, deleted)
VALUES ('person_resolver_group_tags_001', 'groupMembersByTag', '用户组成员标签',
 '按组内标签筛选成员；空标签表示全组，多标签按或匹配。', 'groupMembersByTagPersonResolver', 1, 1,
 '["ASSIGNEE","CANDIDATE","MULTI_INSTANCE"]', '{}', 0, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
