-- 组织部门表
CREATE TABLE IF NOT EXISTS sys_organization (
    id VARCHAR(64) PRIMARY KEY COMMENT '主键ID',
    org_code VARCHAR(100) NOT NULL COMMENT '组织编码（唯一）',
    org_name VARCHAR(100) NOT NULL COMMENT '组织名称',
    type VARCHAR(20) NOT NULL COMMENT '类型：org-组织，dept-部门',
    parent_id VARCHAR(64) DEFAULT '0' COMMENT '父级ID（顶级为0）',
    level INT DEFAULT 0 COMMENT '层级（0为顶级）',
    path VARCHAR(500) DEFAULT '/' COMMENT '完整路径，如：/0/1/5/10/',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    leader_id VARCHAR(64) COMMENT '负责人ID',
    leader_name VARCHAR(100) COMMENT '负责人名称（冗余）',
    phone VARCHAR(50) COMMENT '联系电话',
    email VARCHAR(100) COMMENT '邮箱',
    address VARCHAR(200) COMMENT '地址',
    status VARCHAR(10) DEFAULT '0' COMMENT '状态：0-启用，1-禁用',
    description VARCHAR(500) COMMENT '描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '是否删除：0-未删除 1-已删除',
    UNIQUE KEY uk_org_code (org_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_type (type),
    INDEX idx_path (path),
    INDEX idx_status (status),
    INDEX idx_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织部门表';

-- 给用户表添加组织和部门字段
ALTER TABLE sys_user 
ADD COLUMN org_id VARCHAR(64) COMMENT '组织ID',
ADD COLUMN dept_id VARCHAR(64) COMMENT '部门ID',
ADD INDEX idx_org_id (org_id),
ADD INDEX idx_dept_id (dept_id);

-- 插入默认数据
INSERT INTO sys_organization (id, org_code, org_name, type, parent_id, level, path, sort_order, status) VALUES
('1', 'ROOT', '总公司', 'org', '0', 0, '/0/1/', 1, '0'),
('2', 'DEV_DEPT', '研发中心', 'dept', '1', 1, '/0/1/2/', 1, '0'),
('3', 'HR_DEPT', '人力资源部', 'dept', '1', 1, '/0/1/3/', 2, '0'),
('4', 'FINANCE_DEPT', '财务部', 'dept', '1', 1, '/0/1/4/', 3, '0');
