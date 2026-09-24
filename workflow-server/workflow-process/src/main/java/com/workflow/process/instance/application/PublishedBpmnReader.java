package com.workflow.process.instance.application;

import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 实例、详情、进度共用的已发布 BPMN 读取入口，绝不回退到当前草稿。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishedBpmnReader {
    private final RepositoryService repositoryService;
    private final ProcessPublishedSnapshotService snapshots;

    /** 按实例绑定的定义 ID 读取；定义缺失时返回 null，不改用相同 key 的最新版本。 */
    public String read(String processDefinitionId) {
        if (processDefinitionId == null) return null;
        try {
            return read(repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId).singleResult());
        } catch (RuntimeException exception) {
            log.warn("读取流程定义 BPMN 失败: processDefinitionId={}", processDefinitionId, exception);
            return null;
        }
    }

    /**
     * 复用调用方已查询的定义，按 Model、部署原文、对应发布快照依次回退。
     * 返回原始 XML 以保留 DI 图形信息；资源缺失或读取失败不阻断详情展示。
     */
    public String read(ProcessDefinition definition) {
        if (definition == null) return null;
        try {
            var model = repositoryService.getModel(definition.getId());
            if (model != null) {
                byte[] bytes = repositoryService.getModelEditorSource(model.getId());
                String xml = bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
                if (StringUtils.hasText(xml)) return xml;
            }
        } catch (Exception exception) {
            log.debug("无法从 Model 获取 BPMN XML，尝试部署资源", exception);
        }
        try {
            if (definition.getResourceName() != null) {
                var deployment = repositoryService.createDeploymentQuery()
                        .deploymentId(definition.getDeploymentId()).singleResult();
                if (deployment != null) {
                    // 各入口都关闭资源流，避免重复读取详情时积累未释放的资源。
                    try (InputStream stream = repositoryService.getResourceAsStream(
                            deployment.getId(), definition.getResourceName())) {
                        if (stream != null) {
                            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                            if (StringUtils.hasText(xml)) return xml;
                        }
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("无法读取部署 BPMN 资源: processDefinitionId={}", definition.getId(), exception);
        }
        try {
            var published = snapshots.getVersionByProcessDefinitionId(definition.getId());
            return published != null && StringUtils.hasText(published.getBpmnXml())
                    ? published.getBpmnXml() : null;
        } catch (RuntimeException exception) {
            log.warn("无法读取部署对应的发布 BPMN 快照: processDefinitionId={}", definition.getId(), exception);
            return null;
        }
    }
}
