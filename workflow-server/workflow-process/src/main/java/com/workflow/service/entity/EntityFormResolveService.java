package com.workflow.service.entity;

import com.workflow.contracts.entity.EntityFormBinding;
import com.workflow.contracts.entity.EntityFormRuntimeContext;
import com.workflow.contracts.entity.EntityFormRuntimePort;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体表单解析服务。
 *
 * <p>根据实体编码与数据状态，解析新增数据、活动任务或历史任务实际生效的发布表单。
 * 流程模块仅依赖实体表单运行时端口，不直接访问实体定义、表单服务或 Mapper。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormResolveService {

    private final EntityFormRuntimePort entityFormRuntimePort;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    /**
     * 解析实体新增数据时使用的表单。
     *
     * <p>实体绑定流程时取最新流程发布快照中首个用户任务绑定的表单；
     * 未绑定流程或节点没有表单时回落到实体默认表单。</p>
     */
    public Map<String, Object> resolveFormForNewData(String entityCode) {
        EntityFormRuntimeContext context =
                entityFormRuntimePort.findContext(entityCode).orElse(null);
        if (context == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }
        if (context.processDefinitionId() == null || !context.workflowEnabled()) {
            return context.defaultForm();
        }

        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(
                        context.processDefinitionId());
        if (processConfig == null) {
            return context.defaultForm();
        }

        String firstUserTaskId =
                resolveFirstUserTaskId(processConfig.getBpmnXml());
        Map<String, Object> nodeForm = getNodeBoundEntityForm(
                processConfig.getProcessKey(),
                null,
                firstUserTaskId,
                UiRuntimePurpose.NEW_INSTANCE);
        if (nodeForm != null) {
            log.debug(
                    "新增数据使用首节点表单: processConfigId={}, nodeId={}, formId={}",
                    processConfig.getId(),
                    firstUserTaskId,
                    nodeForm.get("id"));
            return nodeForm;
        }
        return context.defaultForm();
    }

    /**
     * 解析查看实体数据时使用的表单。
     *
     * <p>运行中流程按当前活动任务解析；流程已结束时按最后一个历史任务及原流程发布快照解析。</p>
     */
    public Map<String, Object> resolveFormForViewData(
            String entityCode,
            String entityDataId) {
        EntityFormRuntimeContext context =
                entityFormRuntimePort.findContext(entityCode).orElse(null);
        if (context == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }
        if (!context.workflowEnabled()) {
            return context.defaultForm();
        }

        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceBusinessKey(entityDataId)
                .singleResult();
        if (processInstance == null) {
            HistoricProcessInstance historicInstance = historyService
                    .createHistoricProcessInstanceQuery()
                    .processInstanceBusinessKey(entityDataId)
                    .list()
                    .stream()
                    .max(java.util.Comparator.comparing(
                            HistoricProcessInstance::getStartTime,
                            java.util.Comparator.nullsLast(
                                    java.util.Comparator.naturalOrder())))
                    .orElse(null);
            if (historicInstance == null) {
                return context.defaultForm();
            }
            HistoricTaskInstance historicTask = historyService
                    .createHistoricTaskInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .list()
                    .stream()
                    .max(java.util.Comparator.comparing(
                            item -> item.getEndTime() == null
                                    ? item.getStartTime()
                                    : item.getEndTime(),
                            java.util.Comparator.nullsLast(
                                    java.util.Comparator.naturalOrder())))
                    .orElse(null);
            return historicTask == null
                    ? null
                    : getNodeBoundEntityForm(
                            null,
                            historicInstance.getProcessDefinitionId(),
                            historicTask.getTaskDefinitionKey(),
                            UiRuntimePurpose.HISTORICAL);
        }

        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        if (currentTask == null) {
            return context.defaultForm();
        }

        Map<String, Object> nodeForm = getNodeBoundEntityForm(
                null,
                processInstance.getProcessDefinitionId(),
                currentTask.getTaskDefinitionKey(),
                UiRuntimePurpose.ACTIVE_TASK);
        return nodeForm != null ? nodeForm : context.defaultForm();
    }

    /**
     * 从开始事件出发按广度优先顺序解析首个可达用户任务。
     */
    String resolveFirstUserTaskId(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true);
            factory.setFeature(
                    "http://xml.org/sax/features/external-general-entities",
                    false);
            factory.setFeature(
                    "http://xml.org/sax/features/external-parameter-entities",
                    false);

            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(
                            bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Set<String> userTaskIds = elementIds(
                    document.getElementsByTagNameNS("*", "userTask"));
            if (userTaskIds.isEmpty()) {
                return null;
            }

            Map<String, List<String>> outgoingTargets = new HashMap<>();
            NodeList sequenceFlows =
                    document.getElementsByTagNameNS("*", "sequenceFlow");
            for (int index = 0; index < sequenceFlows.getLength(); index++) {
                Element sequenceFlow = (Element) sequenceFlows.item(index);
                String sourceRef = sequenceFlow.getAttribute("sourceRef");
                String targetRef = sequenceFlow.getAttribute("targetRef");
                if (!sourceRef.isBlank() && !targetRef.isBlank()) {
                    outgoingTargets
                            .computeIfAbsent(
                                    sourceRef,
                                    key -> new ArrayList<>())
                            .add(targetRef);
                }
            }

            ArrayDeque<String> queue = new ArrayDeque<>(elementIds(
                    document.getElementsByTagNameNS("*", "startEvent")));
            Set<String> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                if (userTaskIds.contains(current)) {
                    return current;
                }
                for (String target :
                        outgoingTargets.getOrDefault(current, List.of())) {
                    queue.addLast(target);
                }
            }
            return userTaskIds.iterator().next();
        } catch (Exception exception) {
            log.warn("解析流程首个用户任务失败: {}", exception.getMessage());
            return null;
        }
    }

    private Set<String> elementIds(NodeList elements) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        for (int index = 0; index < elements.getLength(); index++) {
            Element element = (Element) elements.item(index);
            String id = element.getAttribute("id");
            if (!id.isBlank()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<String, Object> getNodeBoundEntityForm(
            String processKey,
            String processDefinitionId,
            String nodeId,
            UiRuntimePurpose purpose) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        ProcessPublishedSnapshotService.PublishedNodeForms published;
        if (processDefinitionId != null
                && !processDefinitionId.isBlank()) {
            published = processPublishedSnapshotService
                    .getNodeFormsContextByProcessDefinitionId(
                            processDefinitionId,
                            nodeId);
        } else if (processKey != null && !processKey.isBlank()) {
            published = processPublishedSnapshotService
                    .getNodeFormsContext(processKey, nodeId);
        } else {
            return null;
        }

        List<ProcessNodeForm> nodeForms = published.nodeForms();
        if (nodeForms == null || nodeForms.isEmpty()) {
            return null;
        }
        EntityFormBinding binding = toBinding(nodeForms.get(0));
        String processVersionHistoryId = published.history().getId();
        if (UiRuntimePurpose.NEW_INSTANCE.equals(purpose)) {
            entityFormRuntimePort.requireCurrentBindingForNewData(
                    binding,
                    processVersionHistoryId);
        }
        return entityFormRuntimePort.findFormByBinding(
                binding,
                processVersionHistoryId,
                purpose);
    }

    private EntityFormBinding toBinding(ProcessNodeForm binding) {
        return new EntityFormBinding(
                binding.getNodeId(),
                binding.getFormId(),
                binding.getFormReleaseId(),
                binding.getFormReleaseVersion());
    }
}
