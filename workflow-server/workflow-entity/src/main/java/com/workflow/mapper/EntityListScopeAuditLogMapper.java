package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实体列表数据范围审计日志 Mapper
 * 
 * 数据范围操作的审计日志持久化接口，目前仅继承通用 CRUD 能力，暂无自定义方法。
 */
@Mapper
public interface EntityListScopeAuditLogMapper extends BaseMapper<EntityListScopeAuditLog> {
}
