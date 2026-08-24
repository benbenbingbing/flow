package com.workflow.process.migration.application;

import com.workflow.process.migration.application.ProcessInstanceMigrationModels.SafetyAssessment;
import com.workflow.process.migration.application.ProcessInstanceMigrationModels.SafetyInput;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Flowable 校验之外的平台迁移白名单和可逆性边界。 */
@Component
public class ProcessMigrationSafetyPolicy {

    /**
     * 校验同一流程 Key、活动节点映射、多实例和待执行作业等风险。
     * 有待执行作业或多实例时允许干运行给出结论，但默认不可自动回迁。
     */
    public SafetyAssessment assess(SafetyInput input) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (input == null) {
            return new SafetyAssessment(false, false, List.of("迁移安全输入不能为空"), List.of());
        }
        if (!StringUtils.hasText(input.sourceProcessKey())
                || !input.sourceProcessKey().equals(input.targetProcessKey())) {
            blockers.add("仅允许在相同流程 Key 的发布版本之间迁移");
        }
        if (input.suspended()) blockers.add("流程实例已挂起，必须先恢复后再迁移");
        if (input.activeActivityIds() == null || input.activeActivityIds().isEmpty()) {
            blockers.add("流程实例没有可迁移的活动节点");
        }
        Map<String, String> mappings = input.activityMappings() == null ? Map.of() : input.activityMappings();
        for (String activityId : input.activeActivityIds() == null
                ? List.<String>of() : input.activeActivityIds()) {
            String target = mappings.getOrDefault(activityId, activityId);
            if (!StringUtils.hasText(target)) {
                blockers.add("活动节点缺少目标映射: " + activityId);
            }
        }
        if (input.multiInstance()) {
            warnings.add("实例处于多实例结构，必须依赖 Flowable 迁移校验并禁止自动回迁");
        }
        if (input.pendingJobCount() > 0) {
            warnings.add("实例存在 " + input.pendingJobCount() + " 个待执行作业，迁移后需核对作业处理器");
        }
        if (input.eventSubscriptionCount() > 0) {
            warnings.add("实例存在事件订阅，迁移后需核对消息和信号兼容性");
        }
        boolean reversible = blockers.isEmpty()
                && !input.multiInstance()
                && input.pendingJobCount() == 0
                && input.eventSubscriptionCount() == 0;
        return new SafetyAssessment(blockers.isEmpty(), reversible,
                List.copyOf(blockers), List.copyOf(warnings));
    }
}
