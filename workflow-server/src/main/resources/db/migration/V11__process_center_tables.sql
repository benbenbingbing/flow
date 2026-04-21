-- 流程任务实例扩展表（用于流程中心统一查询）
CREATE TABLE IF NOT EXISTS process_task_instance (
    id VARCHAR(64) PRIMARY KEY COMMENT '任务实例ID',
    process_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    task_id VARCHAR(64) COMMENT 'Flowable任务ID',
    task_key VARCHAR(100) COMMENT '任务节点Key',
    task_name VARCHAR(200) COMMENT '任务名称',
    process_definition_id VARCHAR(64) COMMENT '流程定义ID',
    process_name VARCHAR(200) COMMENT '流程名称',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    entity_data_id VARCHAR(64) COMMENT '关联实体数据ID',
    business_key VARCHAR(200) COMMENT '业务主键',
    assignee_id VARCHAR(64) COMMENT '被指派人ID',
    assignee_name VARCHAR(100) COMMENT '被指派人姓名',
    owner_id VARCHAR(64) COMMENT '任务所有人ID',
    candidate_users TEXT COMMENT '候选人ID列表（JSON）',
    candidate_groups TEXT COMMENT '候选组列表（JSON）',
    task_type VARCHAR(20) COMMENT '任务类型：TODO待办/DONE已办/DRAFT草稿/CC抄送',
    action_type VARCHAR(50) COMMENT '操作类型：SUBMIT/APPROVE/REJECT/TRANSFER/RETURN/DELEGATE',
    action_comment TEXT COMMENT '处理意见',
    form_data JSON COMMENT '表单数据快照',
    due_time DATETIME COMMENT '截止时间',
    priority INT DEFAULT 50 COMMENT '优先级 0-100',
    is_read TINYINT DEFAULT 0 COMMENT '是否已读',
    read_time DATETIME COMMENT '阅读时间',
    start_time DATETIME COMMENT '任务开始时间',
    end_time DATETIME COMMENT '任务结束时间',
    duration_ms BIGINT COMMENT '处理耗时（毫秒）',
    parent_task_id VARCHAR(64) COMMENT '父任务ID（用于会签）',
    root_task_id VARCHAR(64) COMMENT '根任务ID',
    delegation_state VARCHAR(20) COMMENT '委托状态：PENDING/RESOLVED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_process_instance (process_instance_id),
    INDEX idx_assignee_type (assignee_id, task_type),
    INDEX idx_entity (entity_code, entity_data_id),
    INDEX idx_business_key (business_key),
    INDEX idx_start_time (start_time),
    INDEX idx_due_time (due_time),
    INDEX idx_task_type (task_type, is_read)
) COMMENT='流程任务实例表';

-- 常用审批意见表
CREATE TABLE IF NOT EXISTS process_common_opinion (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    opinion_content VARCHAR(500) NOT NULL COMMENT '意见内容',
    opinion_type VARCHAR(20) DEFAULT 'APPROVE' COMMENT '意见类型：APPROVE同意/REJECT驳回/TRANSFER转办',
    sort_order INT DEFAULT 0 COMMENT '排序',
    use_count INT DEFAULT 0 COMMENT '使用次数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, sort_order)
) COMMENT='常用审批意见';

-- 流程操作记录表（详细审计）
CREATE TABLE IF NOT EXISTS process_operation_log (
    id VARCHAR(64) PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：START/CLAIM/COMPLETE/TRANSFER/DELEGATE/REJECT/RETURN/CC',
    operator_id VARCHAR(64) COMMENT '操作人ID',
    operator_name VARCHAR(100),
    operation_time DATETIME COMMENT '操作时间',
    operation_comment TEXT,
    old_value TEXT COMMENT '旧值（JSON）',
    new_value TEXT COMMENT '新值（JSON）',
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_process (process_instance_id, operation_time),
    INDEX idx_operator (operator_id, operation_time)
) COMMENT='流程操作日志';

-- 流程草稿箱表
CREATE TABLE IF NOT EXISTS process_draft (
    id VARCHAR(64) PRIMARY KEY,
    draft_code VARCHAR(100) UNIQUE COMMENT '草稿编码',
    process_definition_id VARCHAR(64) COMMENT '流程定义ID',
    process_name VARCHAR(200) COMMENT '流程名称',
    entity_code VARCHAR(100) COMMENT '关联实体编码',
    entity_data_id VARCHAR(64) COMMENT '关联实体数据ID（临时数据）',
    business_key VARCHAR(200) COMMENT '业务主键',
    form_data JSON NOT NULL COMMENT '表单数据',
    draft_title VARCHAR(500) COMMENT '草稿标题',
    draft_summary TEXT COMMENT '草稿摘要',
    user_id VARCHAR(64) NOT NULL COMMENT '创建人ID',
    user_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE有效/SUBMITTED已提交/DELETED已删除',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id, status, updated_at),
    INDEX idx_process (process_definition_id),
    INDEX idx_entity (entity_code, entity_data_id)
) COMMENT='流程草稿箱';
