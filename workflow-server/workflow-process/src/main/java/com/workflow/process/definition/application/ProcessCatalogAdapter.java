package com.workflow.process.definition.application;

import com.workflow.contracts.process.port.ProcessCatalogPort;
import com.workflow.contracts.process.model.ProcessCatalogItem;
import com.workflow.contracts.process.model.ProcessBindingState;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigInteger;

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
    /** 流程发布版本 Mapper */
    private final ProcessVersionHistoryMapper versionHistoryMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.process.status.application.ProcessCancellationRequirements cancellationRequirements;
    @org.springframework.beans.factory.annotation.Autowired
    private org.flowable.engine.RepositoryService repositoryService;
    @org.springframework.beans.factory.annotation.Autowired
    private org.flowable.engine.RuntimeService runtimeService;

    /** 旧实例依赖其部署版本；配置草稿改动不能使旧实例失去终止/撤回目标。 */
    @Override
    public java.util.Set<String> requiredEndStatusCategories(String processId) {
        var process = processMapper.selectByIdForUpdate(processId);
        if (process == null) throw new IllegalArgumentException("流程定义不存在: " + processId);
        var result = new java.util.LinkedHashSet<>(cancellationRequirements.requiredCategories(process.getBpmnXml()));
        var definitions = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(process.getProcessKey()).orderByProcessDefinitionVersion().desc().list();
        for (int i = 0; i < definitions.size(); i++) {
            var definition = definitions.get(i);
            if (i == 0 || runtimeService.createProcessInstanceQuery().processDefinitionId(definition.getId()).count() > 0) {
                result.addAll(cancellationRequirements.requiredCategories(repositoryService.getBpmnModel(definition.getId())));
            }
        }
        return java.util.Set.copyOf(result);
    }

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

    /**
     * 与流程发布共用配置行锁，返回实体绑定变更所需的发布状态。
     *
     * <p>调用方已开启事务。这里仍会自行去重、排序，因此涉及旧、新两个流程的
     * 换绑始终保持一致锁序，避免并发换绑形成死锁。</p>
     *
     * @param processIds 流程ID 集合，供本方法锁定绑定{@code states}时使用
     * @return 绑定{@code states}键值结果，供调用方继续处理
     */
    @Override
    public Map<String, ProcessBindingState> lockBindingStates(
            Collection<String> processIds) {
        Map<String, ProcessBindingState> states = new LinkedHashMap<>();
        if (processIds == null) {
            return states;
        }
        List<String> orderedIds = processIds.stream()
                .map(this::canonicalProcessId)
                .filter(processId -> processId != null)
                .distinct()
                .sorted()
                .toList();
        for (String processId : orderedIds) {
            ProcessDefinitionConfig process =
                    processMapper.selectAnyByIdForBindingUpdate(processId);
            if (process == null) {
                continue;
            }
            ProcessCatalogItem item = new ProcessCatalogItem(
                    process.getId(),
                    process.getProcessKey(),
                    process.getProcessName(),
                    process.getStatus() == null
                            ? null
                            : process.getStatus().name());
            states.put(processId, new ProcessBindingState(
                    item,
                    process.getDeleted() == null
                            || process.getDeleted() == 0,
                    versionHistoryMapper.countPublishedVersions(
                            process.getId()) > 0));
        }
        return states;
    }

    /**
     * process_definition_config.id 是 BIGINT，只接受正整数并消除数字别名。
     *
     * @param processId 流程ID，后续用于处理规范流程ID时定位或关联目标
     * @return 处理后的规范流程ID文本，供调用方比较或展示
     */
    private String canonicalProcessId(String processId) {
        if (processId == null || processId.isBlank()) {
            return null;
        }
        try {
            BigInteger value = new BigInteger(processId.trim());
            return value.signum() > 0 ? value.toString() : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
