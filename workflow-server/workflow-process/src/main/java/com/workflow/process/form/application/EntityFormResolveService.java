package com.workflow.process.form.application;

import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.form.model.EntityFormBinding;
import com.workflow.contracts.entity.form.model.EntityFormRuntimeContext;
import com.workflow.contracts.entity.form.port.EntityFormRuntimePort;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
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
     * <p>优先使用实体明确配置的默认表单；仅当实体没有默认表单时，
     * 才回退到最新流程发布快照中首个用户任务绑定的表单。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 表单新数据键值结果，供调用方继续处理
     */
    public Map<String, Object> resolveFormForNewData(String entityCode) {
        log.info(
                "开始解析新增数据表单: entityCode={}",
                LogValue.safe(entityCode));
        EntityFormRuntimeContext context =
                entityFormRuntimePort.findContext(entityCode).orElse(null);
        if (context == null) {
            log.info(
                    "新增数据表单解析无结果: entityCode={}, reason=ENTITY_NOT_FOUND",
                    LogValue.safe(entityCode));
            return null;
        }
        if (context.defaultForm() != null) {
            log.info(
                    "新增数据表单解析完成: entityCode={}, entityId={}, source=DEFAULT_FORM, formId={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(context.entityId()),
                    LogValue.safe(context.defaultForm().get("id")));
            return context.defaultForm();
        }
        if (context.processDefinitionId() == null || !context.workflowEnabled()) {
            log.info(
                    "新增数据表单解析无结果: entityCode={}, entityId={}, workflowEnabled={}, processDefinitionId={}, reason=NO_DEFAULT_OR_WORKFLOW",
                    LogValue.safe(entityCode),
                    LogValue.safe(context.entityId()),
                    context.workflowEnabled(),
                    LogValue.safe(context.processDefinitionId()));
            return null;
        }

        ProcessDefinitionConfig processConfig =
                processDefinitionConfigMapper.selectById(
                        context.processDefinitionId());
        if (processConfig == null) {
            log.info(
                    "新增数据表单解析无结果: entityCode={}, entityId={}, processDefinitionId={}, reason=PROCESS_CONFIG_NOT_FOUND",
                    LogValue.safe(entityCode),
                    LogValue.safe(context.entityId()),
                    LogValue.safe(context.processDefinitionId()));
            return null;
        }

        String firstUserTaskId =
                resolveFirstUserTaskId(processConfig.getBpmnXml());
        Map<String, Object> nodeForm = getNodeBoundEntityForm(
                processConfig.getProcessKey(),
                null,
                firstUserTaskId,
                UiRuntimePurpose.NEW_INSTANCE);
        if (nodeForm != null) {
            log.info(
                    "新增数据表单解析完成: entityCode={}, entityId={}, processConfigId={}, nodeId={}, formId={}, source=FIRST_PROCESS_NODE",
                    LogValue.safe(entityCode),
                    LogValue.safe(context.entityId()),
                    LogValue.safe(processConfig.getId()),
                    LogValue.safe(firstUserTaskId),
                    LogValue.safe(nodeForm.get("id")));
            return nodeForm;
        }
        log.info(
                "新增数据表单解析无结果: entityCode={}, entityId={}, processConfigId={}, nodeId={}, reason=NODE_FORM_NOT_FOUND",
                LogValue.safe(entityCode),
                LogValue.safe(context.entityId()),
                LogValue.safe(processConfig.getId()),
                LogValue.safe(firstUserTaskId));
        return null;
    }

    /**
     * 解析查看实体数据时使用的表单。
     *
     * <p>运行中流程按当前活动任务解析；流程已结束时按最后一个历史任务及原流程发布快照解析。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 表单视图数据键值结果，供调用方继续处理
     */
    public Map<String, Object> resolveFormForViewData(
            String entityCode,
            String entityDataId) {
        log.info(
                "开始解析查看数据表单: entityCode={}, recordId={}",
                LogValue.safe(entityCode),
                LogValue.safe(entityDataId));
        EntityFormRuntimeContext context =
                entityFormRuntimePort.findContext(entityCode).orElse(null);
        if (context == null) {
            log.info(
                    "查看数据表单解析无结果: entityCode={}, recordId={}, reason=ENTITY_NOT_FOUND",
                    LogValue.safe(entityCode),
                    LogValue.safe(entityDataId));
            return null;
        }
        if (!context.workflowEnabled()) {
            log.info(
                    "查看数据表单解析完成: entityCode={}, recordId={}, formId={}, source=DEFAULT_FORM_NON_WORKFLOW",
                    LogValue.safe(entityCode),
                    LogValue.safe(entityDataId),
                    LogValue.safe(
                            context.defaultForm() == null
                                    ? null
                                    : context.defaultForm().get("id")));
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
                log.info(
                        "查看数据表单回退默认表单: entityCode={}, recordId={}, formId={}, reason=PROCESS_INSTANCE_NOT_FOUND",
                        LogValue.safe(entityCode),
                        LogValue.safe(entityDataId),
                        LogValue.safe(
                                context.defaultForm() == null
                                        ? null
                                        : context.defaultForm().get("id")));
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
            if (historicTask == null) {
                log.info(
                        "查看数据表单回退默认表单: entityCode={}, recordId={}, processInstanceId={}, formId={}, reason=HISTORIC_TASK_NOT_FOUND",
                        LogValue.safe(entityCode),
                        LogValue.safe(entityDataId),
                        LogValue.safe(historicInstance.getId()),
                        LogValue.safe(
                                context.defaultForm() == null
                                        ? null
                                        : context.defaultForm().get("id")));
                return context.defaultForm();
            }
            Map<String, Object> nodeForm = getNodeBoundEntityForm(
                    null,
                    historicInstance.getProcessDefinitionId(),
                    historicTask.getTaskDefinitionKey(),
                    UiRuntimePurpose.HISTORICAL);
            log.info(
                    "查看数据表单解析完成: entityCode={}, recordId={}, processInstanceId={}, nodeId={}, formId={}, source={}",
                    LogValue.safe(entityCode),
                    LogValue.safe(entityDataId),
                    LogValue.safe(historicInstance.getId()),
                    LogValue.safe(historicTask.getTaskDefinitionKey()),
                    LogValue.safe(
                            nodeForm == null
                                    ? context.defaultForm() == null
                                            ? null
                                            : context.defaultForm().get("id")
                                    : nodeForm.get("id")),
                    nodeForm == null
                            ? "DEFAULT_FORM"
                            : "HISTORICAL_PROCESS_NODE");
            return nodeForm != null ? nodeForm : context.defaultForm();
        }

        Task currentTask = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .active()
                .singleResult();
        if (currentTask == null) {
            log.info(
                    "查看数据表单回退默认表单: entityCode={}, recordId={}, processInstanceId={}, formId={}, reason=ACTIVE_TASK_NOT_FOUND",
                    LogValue.safe(entityCode),
                    LogValue.safe(entityDataId),
                    LogValue.safe(processInstance.getId()),
                    LogValue.safe(
                            context.defaultForm() == null
                                    ? null
                                    : context.defaultForm().get("id")));
            return context.defaultForm();
        }

        Map<String, Object> nodeForm = getNodeBoundEntityForm(
                null,
                processInstance.getProcessDefinitionId(),
                currentTask.getTaskDefinitionKey(),
                UiRuntimePurpose.ACTIVE_TASK,
                currentTask.getId(),
                processInstance.getId(),
                entityCode,
                entityDataId);
        log.info(
                "查看数据表单解析完成: entityCode={}, recordId={}, processInstanceId={}, nodeId={}, formId={}, source={}",
                LogValue.safe(entityCode),
                LogValue.safe(entityDataId),
                LogValue.safe(processInstance.getId()),
                LogValue.safe(currentTask.getTaskDefinitionKey()),
                LogValue.safe(
                        nodeForm == null
                                ? context.defaultForm() == null
                                        ? null
                                        : context.defaultForm().get("id")
                                : nodeForm.get("id")),
                nodeForm == null
                        ? "DEFAULT_FORM"
                        : "ACTIVE_PROCESS_NODE");
        return nodeForm != null ? nodeForm : context.defaultForm();
    }

    /**
     * 从开始事件出发按广度优先顺序解析首个可达用户任务。
     *
     * @param bpmnXml BPMNXML，供本方法解析首个用户任务ID时使用
     * @return 解析后的首个用户任务ID文本，供调用方比较或展示
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

    /**
     * 整理元素ID 集合数据，供调用方遍历或继续处理。
     *
     * @param elements {@code elements}，供本方法处理元素ID 集合时使用
     * @return 实体表单{@code resolve}集合，供调用方遍历或展示
     */
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
     * 读取节点绑定实体表单；查询结果供调用方展示或继续处理。
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param nodeId 节点ID，后续用于读取节点绑定实体表单时定位或关联目标
     * @param purpose 用途，供本方法读取节点绑定实体表单时使用
     * @return 节点绑定实体表单键值结果，供调用方继续处理
     */
    private Map<String, Object> getNodeBoundEntityForm(
            String processKey,
            String processDefinitionId,
            String nodeId,
            UiRuntimePurpose purpose) {
        return getNodeBoundEntityForm(
                processKey, processDefinitionId, nodeId, purpose,
                null, null, null, null);
    }

    /**
     * 活动任务解析额外携带服务端 Flowable task/instance/record 主体，签发的
     * 发布令牌因此不能移植到同节点的另一业务实例。
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param nodeId 节点ID，后续用于读取节点绑定实体表单时定位或关联目标
     * @param purpose 用途，作为 {@code UiRuntimePurpose.ACTIVE_TASK.equals} 的输入影响后续处理
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 节点绑定实体表单键值结果，供调用方继续处理
     */
    private Map<String, Object> getNodeBoundEntityForm(
            String processKey,
            String processDefinitionId,
            String nodeId,
            UiRuntimePurpose purpose,
            String taskId,
            String processInstanceId,
            String entityCode,
            String recordId) {
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
            log.info(
                    "流程节点未绑定表单: processKey={}, processDefinitionId={}, historyId={}, nodeId={}, purpose={}",
                    LogValue.safe(processKey),
                    LogValue.safe(processDefinitionId),
                    LogValue.safe(
                            published.history() == null
                                    ? null
                                    : published.history().getId()),
                    LogValue.safe(nodeId),
                    LogValue.safe(purpose));
            return null;
        }
        EntityFormBinding binding = toBinding(nodeForms.get(0));
        String processVersionHistoryId = published.history().getId();
        if (UiRuntimePurpose.NEW_INSTANCE.equals(purpose)) {
            entityFormRuntimePort.requireCurrentBindingForNewData(
                    binding,
                    processVersionHistoryId);
        }
        // 只有 ACTIVE_TASK 需要新增的主体绑定重载；新建/历史路径继续调用旧
        // 公共端口签名，保持外部适配器和既有行为兼容。
        Map<String, Object> result =
                UiRuntimePurpose.ACTIVE_TASK.equals(purpose)
                        ? entityFormRuntimePort.findFormByBinding(
                                binding,
                                UiRuntimeResolutionContext.activeTask(
                                        processVersionHistoryId,
                                        nodeId,
                                        taskId,
                                        processInstanceId,
                                        entityCode,
                                        recordId))
                        : entityFormRuntimePort.findFormByBinding(
                                binding,
                                processVersionHistoryId,
                                purpose);
        log.info(
                "流程节点表单解析完成: processKey={}, processDefinitionId={}, historyId={}, nodeId={}, boundFormId={}, pinnedReleaseId={}, pinnedVersion={}, resolvedFormId={}, purpose={}",
                LogValue.safe(processKey),
                LogValue.safe(processDefinitionId),
                LogValue.safe(processVersionHistoryId),
                LogValue.safe(nodeId),
                LogValue.safe(binding.formId()),
                LogValue.safe(binding.formReleaseId()),
                binding.formReleaseVersion(),
                LogValue.safe(
                        result == null ? null : result.get("id")),
                LogValue.safe(purpose));
        return result;
    }

    /**
     * 转换为绑定；输出作为后续校验或处理的输入。
     *
     * @param binding 绑定，作为 {@code EntityFormBinding} 的输入影响后续处理
     * @return 转换为后的绑定结果，供调用方继续处理
     */
    private EntityFormBinding toBinding(ProcessNodeForm binding) {
        return new EntityFormBinding(
                binding.getNodeId(),
                binding.getFormId(),
                binding.getFormReleaseId(),
                binding.getFormReleaseVersion());
    }
}
