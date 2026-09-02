package com.workflow.entity.definition.infrastructure.adapter;

import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 实体编码目录适配器
 * 
 * 实现 {@link EntityCodeCatalogPort} 端口契约，作为流程侧查询实体目录的防腐层：
 * 通过 EntityDefinitionMapper 提供全部实体编码集合以及按流程定义 ID 反查实体编码的能力。
 */
@Component
@RequiredArgsConstructor
public class EntityCodeCatalogAdapter implements EntityCodeCatalogPort {

    private final EntityDefinitionMapper entityDefinitionMapper;

    /**
     * 查询全部实体编码集合。
     *
     * @return 实体编码集合
     */
    @Override
    public Set<String> findAllEntityCodes() {
        return new HashSet<>(entityDefinitionMapper.findAllEntityCodes());
    }

    /**
     * 根据流程定义 ID 查询绑定的实体编码。
     *
     * @param processDefinitionId 流程定义 ID，空白时返回 null
     * @return 实体编码，未绑定返回 null
     */
    @Override
    public String findEntityCodeByProcessDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        List<EntityDefinition> bindings = entityDefinitionMapper
                .findAllByProcessDefinitionId(processDefinitionId);
        if (bindings.size() > 1) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_BINDING_AMBIGUOUS",
                    "流程绑定了多个实体，请先修复历史绑定数据: "
                            + processDefinitionId);
        }
        return bindings.isEmpty()
                ? null : bindings.get(0).getEntityCode();
    }
}
