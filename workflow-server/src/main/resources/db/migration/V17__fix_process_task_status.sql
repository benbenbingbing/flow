-- 修复 process_task 表 status 字段类型
-- 将 status 字段从 INT 改为 VARCHAR(20)

ALTER TABLE process_task MODIFY COLUMN status VARCHAR(20) DEFAULT 'todo' COMMENT '状态：todo待办/done已办/transfer已转办/skip已跳过/withdrawn已撤回';
