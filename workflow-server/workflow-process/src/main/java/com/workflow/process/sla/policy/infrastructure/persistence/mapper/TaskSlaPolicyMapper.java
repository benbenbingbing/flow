package com.workflow.process.sla.policy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaPolicy;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskSlaPolicyMapper extends BaseMapper<TaskSlaPolicy> {

    default TaskSlaPolicy findLatestPublished(String policyCode) {
        // 首行限制交给分页插件，避免加载全部结果或在 Mapper 内拼接数据库分页语法。
        return selectList(new Page<TaskSlaPolicy>(1, 1, false), Wrappers.<TaskSlaPolicy>lambdaQuery()
                .eq(TaskSlaPolicy::getPolicyCode, policyCode)
                .eq(TaskSlaPolicy::getStatus, "PUBLISHED")
                .orderByDesc(TaskSlaPolicy::getVersion)).stream().findFirst().orElse(null);
    }

    /** 读取已发布的有效 SLA 策略，按编码及版本倒序排列。 */
    default List<TaskSlaPolicy> findPublished() {
        return selectList(Wrappers.<TaskSlaPolicy>lambdaQuery()
                .eq(TaskSlaPolicy::getStatus, "PUBLISHED")
                .orderByAsc(TaskSlaPolicy::getPolicyCode)
                .orderByDesc(TaskSlaPolicy::getVersion));
    }

    /** 按业务编码读取最大版本；聚合使用标准 SQL，过滤及逻辑删除交给 Wrapper。 */
    default int findMaxVersion(String policyCode) {
        List<Object> values = selectObjs(Wrappers.<TaskSlaPolicy>query()
                .select("COALESCE(MAX(version), 0)").eq("policy_code", policyCode));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).intValue();
    }
}
