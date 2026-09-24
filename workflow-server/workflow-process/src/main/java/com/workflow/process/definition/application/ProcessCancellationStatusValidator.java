package com.workflow.process.definition.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.application.EntitySpecialStatusPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.status.application.ProcessCancellationRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 流程打开终止/撤回能力时，发布前确保绑定实体具有唯一目标状态。 */
@Component
@RequiredArgsConstructor
public class ProcessCancellationStatusValidator {
    private final EntityDefinitionMapper definitions;
    private final EntityStatusMapper statuses;
    private final ProcessCancellationRequirements requirements;

    /** 调用方发布时持有流程配置锁；实体状态保存也获取同一锁，防止校验后配置被并发删除。 */
    public void validate(ProcessDefinitionConfig process) {
        var required = requirements.requiredCategories(process.getBpmnXml());
        for (var entity : definitions.selectList(Wrappers.<EntityDefinition>lambdaQuery()
                .eq(EntityDefinition::getProcessDefinitionId, process.getId()))) {
            EntitySpecialStatusPolicy.validate(statuses.findByEntityCode(entity.getEntityCode()), required);
        }
    }
}
