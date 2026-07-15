package com.workflow.service.entity;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.service.EntityFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormResolveService {

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final ProcessNodeFormMapper processNodeFormMapper;
    private final EntityFormService entityFormService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public EntityForm resolveFormForNewData(String entityCode) {
        EntityDefinition entityDefinition = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entityDefinition == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }

        if (entityDefinition.getProcessDefinitionId() == null
                || !Boolean.TRUE.equals(entityDefinition.getEnableProcess())) {
            return entityFormService.getDefaultForm(entityDefinition.getId());
        }

        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(entityDefinition.getProcessDefinitionId());
        if (processConfig == null) {
            return entityFormService.getDefaultForm(entityDefinition.getId());
        }

        String firstUserTaskId = resolveFirstUserTaskId(processConfig.getBpmnXml());
        EntityForm nodeForm = getNodeBoundEntityForm(processConfig.getId(), firstUserTaskId);
        if (nodeForm != null) {
            log.debug("新增数据使用首节点表单: processConfigId={}, nodeId={}, formId={}",
                    processConfig.getId(), firstUserTaskId, nodeForm.getId());
            return nodeForm;
        }

        return entityFormService.getDefaultForm(entityDefinition.getId());
    }

    public EntityForm resolveFormForViewData(String entityCode, String entityDataId) {
        EntityDefinition entityDefinition = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entityDefinition == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(entityDataId)
                .singleResult();
        if (processInstance == null) {
            return entityFormService.getDefaultForm(entityDefinition.getId());
        }

        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        if (currentTask == null) {
            return entityFormService.getDefaultForm(entityDefinition.getId());
        }

        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(entityDefinition.getProcessDefinitionId());
        if (processConfig == null) {
            return entityFormService.getDefaultForm(entityDefinition.getId());
        }

        EntityForm nodeForm =
                getNodeBoundEntityForm(processConfig.getId(), currentTask.getTaskDefinitionKey());
        return nodeForm != null ? nodeForm : entityFormService.getDefaultForm(entityDefinition.getId());
    }

    String resolveFirstUserTaskId(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return null;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Set<String> userTaskIds = elementIds(document.getElementsByTagNameNS("*", "userTask"));
            if (userTaskIds.isEmpty()) {
                return null;
            }

            Map<String, List<String>> outgoingTargets = new HashMap<>();
            NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
            for (int index = 0; index < sequenceFlows.getLength(); index++) {
                Element sequenceFlow = (Element) sequenceFlows.item(index);
                String sourceRef = sequenceFlow.getAttribute("sourceRef");
                String targetRef = sequenceFlow.getAttribute("targetRef");
                if (!sourceRef.isBlank() && !targetRef.isBlank()) {
                    outgoingTargets.computeIfAbsent(sourceRef, key -> new ArrayList<>()).add(targetRef);
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
                for (String target : outgoingTargets.getOrDefault(current, List.of())) {
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

    private EntityForm getNodeBoundEntityForm(String processConfigId, String nodeId) {
        if (processConfigId == null || nodeId == null || nodeId.isBlank()) {
            return null;
        }
        List<ProcessNodeForm> nodeForms =
                processNodeFormMapper.selectListByNodeId(processConfigId, nodeId);
        if (nodeForms == null || nodeForms.isEmpty()) {
            return null;
        }
        String formId = nodeForms.get(0).getFormId();
        return formId == null ? null : entityFormService.getById(formId);
    }
}
