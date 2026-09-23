package com.workflow.process.sla.policy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaEscalationStep;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 定义任务SLA{@code escalation}步骤的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface TaskSlaEscalationStepMapper
        extends BaseMapper<TaskSlaEscalationStep> {

    /**
     * 读取策略中启用的升级步骤，按执行顺序返回。
     *
     * @param policyId 策略ID，后续用于查询启用策略ID时定位或关联目标
     * @return 任务SLA{@code escalation}步骤集合，供调用方遍历或展示
     */
    default List<TaskSlaEscalationStep> findEnabledByPolicyId(String policyId) {
        return selectList(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId)
                .eq(TaskSlaEscalationStep::getEnabled, 1)
                .orderByAsc(TaskSlaEscalationStep::getSortOrder)
                .orderByAsc(TaskSlaEscalationStep::getCreateTime));
    }

    /**
     * 读取策略的全部升级步骤，包含禁用步骤以支持配置编辑。
     *
     * @param policyId 策略ID，后续用于查询策略ID时定位或关联目标
     * @return 任务SLA{@code escalation}步骤集合，供调用方遍历或展示
     */
    default List<TaskSlaEscalationStep> findByPolicyId(String policyId) {
        return selectList(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId)
                .orderByAsc(TaskSlaEscalationStep::getSortOrder)
                .orderByAsc(TaskSlaEscalationStep::getCreateTime));
    }

    /**
     * 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。
     *
     * @param policyId 策略ID，后续用于删除策略ID时定位或关联目标
     * @return 删除后的策略ID结果，供调用方继续处理
     */
    default int deleteByPolicyId(String policyId) {
        return delete(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId));
    }
}
