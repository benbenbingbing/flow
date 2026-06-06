-- 实体编码规则配置表
-- 用于存储每个实体的数据编码生成规则
CREATE TABLE IF NOT EXISTS `entity_code_rule` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `entity_code` VARCHAR(100) NOT NULL COMMENT '实体编码',
    `prefix` VARCHAR(20) DEFAULT '' COMMENT '编码前缀，如：CG、DD',
    `date_format` VARCHAR(20) DEFAULT 'yyyyMMdd' COMMENT '日期格式，如：yyyyMMdd、yyyy-MM-dd',
    `seq_length` INT DEFAULT 6 COMMENT '序列号位数，如：6表示000001',
    `seq_type` VARCHAR(20) DEFAULT 'DAY' COMMENT '序列号重置周期：DAY按天、MONTH按月、YEAR按年、NEVER不重置',
    `current_seq` INT DEFAULT 0 COMMENT '当前序列号值',
    `seq_date` VARCHAR(20) DEFAULT '' COMMENT '当前序列号对应的日期（用于判断重置）',
    `example` VARCHAR(100) DEFAULT '' COMMENT '编码示例',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entity_code` (`entity_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体编码规则配置表';
