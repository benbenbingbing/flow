-- --------------------------------------------------------
-- 表名: node_config                      -- 节点配置表
-- 说明: 添加 skip_node 字段，支持第一个节点自动跳过功能
-- --------------------------------------------------------

-- 添加 skip_node 字段
ALTER TABLE `node_config`
ADD COLUMN IF NOT EXISTS `skip_node` TINYINT(1) DEFAULT 0 COMMENT '是否跳过此节点（仅第一个用户任务节点可设置）：0-不跳过，1-跳过';

-- 添加索引
CREATE INDEX IF NOT EXISTS `idx_skip_node` ON `node_config` (`skip_node`);
