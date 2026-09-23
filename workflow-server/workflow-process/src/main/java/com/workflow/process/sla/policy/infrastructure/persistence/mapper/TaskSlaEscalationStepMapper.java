package com.workflow.process.sla.policy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.process.sla.policy.infrastructure.persistence.record.TaskSlaEscalationStep;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskSlaEscalationStepMapper
        extends BaseMapper<TaskSlaEscalationStep> {

    /** 读取策略中启用的升级步骤，按执行顺序返回。 */
    default List<TaskSlaEscalationStep> findEnabledByPolicyId(String policyId) {
        return selectList(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId)
                .eq(TaskSlaEscalationStep::getEnabled, 1)
                .orderByAsc(TaskSlaEscalationStep::getSortOrder)
                .orderByAsc(TaskSlaEscalationStep::getCreateTime));
    }

    /** 读取策略的全部升级步骤，包含禁用步骤以支持配置编辑。 */
    default List<TaskSlaEscalationStep> findByPolicyId(String policyId) {
        return selectList(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId)
                .orderByAsc(TaskSlaEscalationStep::getSortOrder)
                .orderByAsc(TaskSlaEscalationStep::getCreateTime));
    }

    /** 该配置表没有逻辑删除字段，使用 BaseMapper 按条件物理删除。 */
    default int deleteByPolicyId(String policyId) {
        return delete(Wrappers.<TaskSlaEscalationStep>lambdaQuery()
                .eq(TaskSlaEscalationStep::getPolicyId, policyId));
    }
}
