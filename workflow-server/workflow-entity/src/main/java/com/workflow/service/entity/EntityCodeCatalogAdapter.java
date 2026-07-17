package com.workflow.service.entity;

import com.workflow.contracts.entity.EntityCodeCatalogPort;
import com.workflow.mapper.EntityDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class EntityCodeCatalogAdapter implements EntityCodeCatalogPort {

    private final EntityDefinitionMapper entityDefinitionMapper;

    @Override
    public Set<String> findAllEntityCodes() {
        return new HashSet<>(entityDefinitionMapper.findAllEntityCodes());
    }

    @Override
    public String findEntityCodeByProcessDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            return null;
        }
        return entityDefinitionMapper.findByProcessDefinitionId(processDefinitionId)
                .map(entity -> entity.getEntityCode())
                .orElse(null);
    }
}
