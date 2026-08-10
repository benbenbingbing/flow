package com.workflow.project.custom;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationStepContext;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体变更步骤扩展示例。
 *
 * <p>编码为 {@value #CODE}，支持四个变更阶段。当前实现只记录上下文并返回
 * {@code ALLOW}，不会修改 payload，也不会额外生成实体变更。</p>
 */
@Slf4j
@Component
public class ProjectCustomMutationStepProvider
        implements EntityMutationStepProvider {

    public static final String CODE =
            "PROJECT_CUSTOM_MUTATION_STEP";

    @Override
    public String getCode() {
        return CODE;
    }

    @Override
    public String getDisplayName() {
        return "项目自定义变更步骤";
    }

    @Override
    public Set<EntityMutationPhase> supportedPhases() {
        return Set.of(EntityMutationPhase.values());
    }

    @Override
    public Map<String, Object> configurationSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "scene", Map.of(
                                "type", "string",
                                "title", "验证场景"),
                        "message", Map.of(
                                "type", "string",
                                "title", "日志说明")),
                "required", List.of("scene"));
    }

    @Override
    public EntityMutationStepResult execute(
            EntityMutationStepContext context) {
        EntityMutationCommand command =
                context == null ? null : context.command();
        Map<String, Object> configuration =
                context == null
                        || context.configuration() == null
                        ? Map.of()
                        : context.configuration();
        log.info(
                "项目实体变更步骤执行: code={}, phase={}, operationId={}, entityCode={}, recordId={}, operationType={}, sourceType={}, businessIntentCode={}, payloadKeys={}, configurationKeys={}",
                CODE,
                context == null ? null : context.phase(),
                LogValue.safe(command == null
                        ? null : command.operationId()),
                LogValue.safe(command == null
                        ? null : command.entityCode()),
                LogValue.safe(command == null
                        ? null : command.recordId()),
                command == null
                        ? null : command.operationType(),
                command == null || command.context() == null
                        ? null : command.context().sourceType(),
                LogValue.safe(command == null
                        || command.context() == null
                        ? null
                        : command.context()
                                .businessIntentCode()),
                context == null
                        || context.workingPayload() == null
                        ? java.util.Set.of()
                        : context.workingPayload().keySet(),
                configuration.keySet());
        return new EntityMutationStepResult(
                EntityMutationStepResult.Decision.ALLOW,
                "项目自定义变更步骤已记录日志",
                Map.of(),
                Map.of(
                        "providerCode", CODE,
                        "phase", String.valueOf(
                                context == null
                                        ? null
                                        : context.phase())));
    }
}
