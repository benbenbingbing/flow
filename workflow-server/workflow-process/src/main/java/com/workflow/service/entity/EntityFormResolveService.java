package com.workflow.service.entity;

import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
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

/**
 * 实体表单解析服务。
 *
 * <p>根据实体编码与数据状态，解析出新增数据、查看数据时应使用的表单：
 * 流程绑定时优先取流程首个/当前节点绑定的表单，否则回落到实体默认表单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFormResolveService {

    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    /**
     * 解析实体新增数据时使用的表单。
     *
     * <p>实体绑定流程时取流程首个用户任务节点绑定的表单；未绑定流程或无节点表单时回落到默认表单。</p>
     *
     * @param entityCode 实体编码
     * @return 运行时表单，不存在时返回 null
     */
    public EntityForm resolveFormForNewData(String entityCode) {
        EntityDefinition entityDefinition = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entityDefinition == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }

        if (entityDefinition.getProcessDefinitionId() == null
                || entityDefinition.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) {
            return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
        }

        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(entityDefinition.getProcessDefinitionId());
        if (processConfig == null) {
            return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
        }

        String firstUserTaskId = resolveFirstUserTaskId(processConfig.getBpmnXml());
        EntityForm nodeForm = getNodeBoundEntityForm(
                processConfig.getProcessKey(),
                null,
                firstUserTaskId);
        if (nodeForm != null) {
            log.debug("新增数据使用首节点表单: processConfigId={}, nodeId={}, formId={}",
                    processConfig.getId(), firstUserTaskId, nodeForm.getId());
            return nodeForm;
        }

        return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
    }

    /**
     * 解析查看实体数据时使用的表单。
     *
     * <p>根据数据对应的流程实例当前任务节点取绑定的表单；无流程或无任务时回落到默认表单。</p>
     *
     * @param entityCode   实体编码
     * @param entityDataId 实体数据ID（作为流程业务Key）
     * @return 运行时表单，不存在时返回 null
     */
    public EntityForm resolveFormForViewData(String entityCode, String entityDataId) {
        EntityDefinition entityDefinition = entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
        if (entityDefinition == null) {
            log.debug("未找到实体定义[{}]", entityCode);
            return null;
        }
        if (entityDefinition.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) {
            return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
        }

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(entityDataId)
                .singleResult();
        if (processInstance == null) {
            return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
        }

        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        if (currentTask == null) {
            return entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
        }

        EntityForm nodeForm =
                getNodeBoundEntityForm(
                        null,
                        processInstance.getProcessDefinitionId(),
                        currentTask.getTaskDefinitionKey());
        return nodeForm != null
                ? nodeForm
                : entityFormRuntimeService.getDefaultForm(entityDefinition.getId());
    }

    /**
     * 解析 BPMN 中首个用户任务节点ID。
     *
     * <p>从开始事件出发按广度优先遍历顺序流，找到的第一个 userTask 即返回；
     * 遍历不到时返回任意一个 userTask，解析失败返回 null。</p>
     *
     * @param bpmnXml BPMN XML 内容
     * @return 首个用户任务节点ID
     */
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

    /** 提取 XML 节点集合中所有非空的 id 属性值，保持顺序去重 */
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

    /**
     * 根据流程标识或流程定义ID查询节点绑定的运行时表单。
     *
     * @param processKey         流程标识（与 processDefinitionId 二选一）
     * @param processDefinitionId 流程定义ID
     * @param nodeId             节点ID
     * @return 运行时表单，无绑定或节点为空时返回 null
     */
    private EntityForm getNodeBoundEntityForm(
            String processKey,
            String processDefinitionId,
            String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        List<ProcessNodeForm> nodeForms;
        if (processDefinitionId != null && !processDefinitionId.isBlank()) {
            nodeForms =
                    processPublishedSnapshotService
                            .getNodeFormsByProcessDefinitionId(
                                    processDefinitionId,
                                    nodeId);
        } else if (processKey != null && !processKey.isBlank()) {
            nodeForms =
                    processPublishedSnapshotService.getNodeForms(
                            processKey,
                            nodeId);
        } else {
            return null;
        }
        if (nodeForms == null || nodeForms.isEmpty()) {
            return null;
        }
        return entityFormRuntimeService.getByBinding(nodeForms.get(0));
    }
}
