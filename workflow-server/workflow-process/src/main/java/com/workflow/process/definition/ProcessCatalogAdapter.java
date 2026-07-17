package com.workflow.process.definition;

import com.workflow.contracts.process.ProcessCatalogPort;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProcessCatalogAdapter implements ProcessCatalogPort {

    private final ProcessDefinitionConfigMapper processMapper;

    @Override
    public Map<String, String> findNamesByIds(Collection<String> processIds) {
        Map<String, String> names = new LinkedHashMap<>();
        if (processIds == null) {
            return names;
        }
        for (String processId : processIds) {
            ProcessDefinitionConfig process = processMapper.selectById(processId);
            if (process != null) {
                names.put(processId, process.getProcessName());
            }
        }
        return names;
    }
}
