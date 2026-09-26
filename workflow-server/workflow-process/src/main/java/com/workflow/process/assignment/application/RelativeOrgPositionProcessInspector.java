package com.workflow.process.assignment.application;

import com.workflow.process.assignment.domain.RelativeOrgPositionConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.application.LegacyMultiInstanceAssignmentParser;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 判断已部署 BPMN 是否实际引用相对组织职务。
 *
 * <p>只有包含相对职务规则的部署才强制捕获组织快照，使历史非相对流程
 * 中无组织归属的合法用户仍可发起；一旦部署引用该 resolver，快照缺失
 * 就会在发起事务内 Fail Closed。</p>
 */
@Service
public class RelativeOrgPositionProcessInspector {

    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化相对组织位置流程{@code inspector}，保存构造参数供后续方法使用。
     *
     * @param repositoryService 仓储服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public RelativeOrgPositionProcessInspector(
            RepositoryService repositoryService,
            ObjectMapper objectMapper) {
        this.repositoryService = repositoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 只读取指定部署的 BPMN 快照，不回查可变草稿。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 需要{@code initiator}组织快照条件成立时为 true，否则为 false
     */
    public boolean requiresInitiatorOrganizationSnapshot(
            String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return false;
        }
        BpmnModel model = repositoryService.getBpmnModel(
                processDefinitionId);
        return model != null
                && model.getMainProcess() != null
                && containsRelativeResolver(
                model.getMainProcess().getFlowElements());
    }

    /**
     * 判断是否包含相对解析器；判断结果决定调用方的后续分支。
     *
     * @param elements {@code elements}，供本方法判断是否包含相对解析器时使用
     * @return 相对解析器条件成立时为 true，否则为 false
     */
    private boolean containsRelativeResolver(
            Collection<FlowElement> elements) {
        if (elements == null) {
            return false;
        }
        for (FlowElement element : elements) {
            if (element instanceof UserTask task
                    && usesRelativeResolver(task)) {
                return true;
            }
            if (element instanceof SubProcess subProcess
                    && containsRelativeResolver(
                    subProcess.getFlowElements())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断使用相对解析器条件是否成立，供调用方选择后续分支。
     *
     * @param task 任务，作为 {@code ConfiguredTaskPropertyReader.read} 的输入影响后续处理
     * @return 使用相对解析器条件成立时为 true，否则为 false
     */
    @SuppressWarnings("unchecked")
    private boolean usesRelativeResolver(UserTask task) {
        String assigneeDocument = ConfiguredTaskPropertyReader.read(
                task, "assigneeConfig");
        String multiDocument = ConfiguredTaskPropertyReader.read(
                task, "multiInstanceConfig");
        try {
            Map<String, Object> primary = Map.of();
            if (StringUtils.hasText(assigneeDocument)) {
                primary = objectMapper.readValue(
                        assigneeDocument, Map.class);
            }
            Map<String, Object> fallback = Map.of();
            if (StringUtils.hasText(multiDocument)) {
                fallback = objectMapper.readValue(
                        multiDocument, Map.class);
            }
            Map<String, Object> config =
                    LegacyMultiInstanceAssignmentParser.mergeConfigs(
                            primary, fallback);
            return RelativeOrgPositionConfig.RESOLVER_CODE.equals(
                    LegacyMultiInstanceAssignmentParser
                            .effectiveResolver(
                                    config,
                                    task.hasMultiInstanceLoopCharacteristics())
                            .resolverCode());
        } catch (Exception exception) {
            // 已部署文档损坏时仍对 resolver 稳定编码做保守检测。
            // 可疑相对职务流程宁可多捕获一份快照，也不得因 JSON
            // 损坏而在启动时绕过安全上下文。
            return containsResolverCode(assigneeDocument)
                    || containsResolverCode(multiDocument);
        }
    }

    /**
     * 判断是否包含解析器编码；判断结果决定调用方的后续分支。
     *
     * @param document 文档，供本方法判断是否包含解析器编码时使用
     * @return 解析器编码条件成立时为 true，否则为 false
     */
    private boolean containsResolverCode(String document) {
        return StringUtils.hasText(document)
                && document.contains(
                RelativeOrgPositionConfig.RESOLVER_CODE);
    }
}
