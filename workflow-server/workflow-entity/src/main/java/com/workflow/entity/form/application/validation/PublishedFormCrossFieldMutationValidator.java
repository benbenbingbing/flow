package com.workflow.entity.form.application.validation;

import com.workflow.entity.form.application.context.FormCrossFieldRuntimeContext;
import com.workflow.entity.form.application.model.PublishedFormRecordView;

import com.workflow.port.entity.model.mutation.EntityMutationCommand;
import com.workflow.port.entity.model.mutation.EntityMutationOperationType;
import com.workflow.port.entity.model.mutation.EntityMutationSourceType;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 在实体事务内对当前提交实际采用的发布表单重检，不扩大为实体全局约束。 */
@Component
@RequiredArgsConstructor
public class PublishedFormCrossFieldMutationValidator {
    private final UiConfigReleaseService releaseService;
    private final PublishedFormCrossFieldValidator validator;
    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.entity.form.application.FormContextDefaults formContextParameters;

    /**
     * 调用方须持有记录锁（新增记录则在创建事务内），传入持久化后的完整记录。
     * 这样并发补丁、字段默认值和持久化转换均已体现在比较值中；失败由外层事务回滚。
     *
     * @param command 本次命令，后续经校验后用于校验已发布表单跨字段变更
     * @param finalRecord {@code final}记录，供本方法校验已发布表单跨字段变更时使用
     */
    public void validate(EntityMutationCommand command, Map<String, Object> finalRecord) {
        if (command.operationType() == EntityMutationOperationType.DELETE) return;
        String mode = command.context().sourceType() == EntityMutationSourceType.APPROVAL_TASK
                ? "approve" : command.operationType() == EntityMutationOperationType.CREATE ? "create" : "edit";
        for (var reference : FormUniqueMutationContext.resolveAll(command.context())) {
            if (command.context().sourceType() == EntityMutationSourceType.APPROVAL_TASK
                    && FormCrossFieldRuntimeContext.isReadonly(command.context().extraParams(), reference.formId())) continue;
            var resolved = releaseService.resolveTrustedEffectiveFormRelease(
                    reference.formId(), reference.releaseId(), reference.releaseVersion(),
                    reference.effectiveReleaseId(), reference.effectiveContentHash(), reference.hotfixTargetId());
            if (resolved != null && resolved.releaseId() != null && resolved.form() != null) {
                Map<String, Object> record = PublishedFormRecordView.flatten(finalRecord);
                if (formContextParameters != null && validator.hasRules(resolved.form()) && formContextParameters.hasRules(resolved.form())) {
                    // 最终校验持有记录锁，按命令中的服务器任务坐标恢复同一入口，不依赖页面的上下文副本。
                    var entry = switch (mode) {
                        case "create" -> releaseService.resolveNewRecordContext(command.entityCode(), reference.formId());
                        case "approve" -> command.context().taskId() == null ? null
                                : releaseService.authorizeRuntimeContext(reference.formId(), Map.of("taskId", command.context().taskId()));
                        default -> null;
                    };
                    record = com.workflow.entity.form.application.FormContextRules.withVariables(record,
                            formContextParameters.valuesFor(resolved.form(), entry, mode));
                }
                validator.validateRecord(resolved.form(), mode, record);
            }
        }
    }
}
