package com.workflow.process.definition;

import com.workflow.contracts.process.ProcessCatalogPort;
import com.workflow.contracts.process.ProcessCatalogItem;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程目录适配器
 * 实现 {@link ProcessCatalogPort} 端口，作为流程定义信息对外暴露的适配层，
 * 将流程定义配置转换为流程目录项供其他模块（如操作日志、实体记录）使用。
 */
@Component
@RequiredArgsConstructor
public class ProcessCatalogAdapter implements ProcessCatalogPort {

    /** 流程定义配置 Mapper */
    private final ProcessDefinitionConfigMapper processMapper;

    /**
     * 根据流程ID集合批量查询流程名称映射。
     *
     * @param processIds 流程定义ID集合，为 null 时返回空映射
     * @return 流程ID到流程名称的有序映射
     */
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

    /**
     * 根据流程ID集合批量查询流程目录项映射。
     *
     * @param processIds 流程定义ID集合，为 null 时返回空映射
     * @return 流程ID到流程目录项（含ID、Key、名称、状态）的有序映射
     */
    @Override
    public Map<String, ProcessCatalogItem> findItemsByIds(Collection<String> processIds) {
        Map<String, ProcessCatalogItem> items = new LinkedHashMap<>();
        if (processIds == null) {
            return items;
        }
        for (String processId : processIds) {
            ProcessDefinitionConfig process = processMapper.selectById(processId);
            if (process != null) {
                items.put(processId, new ProcessCatalogItem(
                        process.getId(),
                        process.getProcessKey(),
                        process.getProcessName(),
                        process.getStatus() == null ? null : process.getStatus().name()));
            }
        }
        return items;
    }
}
