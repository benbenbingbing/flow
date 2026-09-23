package com.workflow.process.assignment.extension;

import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveResult;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.contracts.process.assignment.spi.PersonResolver;
import com.workflow.contracts.process.assignment.model.PersonResolverDescriptor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * 返回流程发起人的内置人员解析器。
 */
@Component
public class ProcessInitiatorPersonResolver implements PersonResolver {

    private static final PersonResolverDescriptor DESCRIPTOR =
            new PersonResolverDescriptor(
                    "processInitiator",
                    "流程发起人",
                    "返回当前流程的发起人，可用于审批人、候选人、会签和知会。",
                    1,
                    1,
                    Set.of(
                            PersonResolveUsage.ASSIGNEE,
                            PersonResolveUsage.CANDIDATE,
                            PersonResolveUsage.MULTI_INSTANCE,
                            PersonResolveUsage.CC),
                    Map.of(),
                    false);

    /**
     * 处理实现来源，并将结果传给后续步骤。
     *
     * @return 处理后的实现来源结果，供调用方继续处理
     */
    @Override
    public ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.PLATFORM;
    }

    /**
     * 处理描述，并将结果传给后续步骤。
     *
     * @return 处理后的描述结果，供调用方继续处理
     */
    @Override
    public PersonResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * 解析流程{@code initiator}人员解析器；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析流程{@code initiator}人员解析器
     * @return 解析后的流程{@code initiator}人员解析器结果，供调用方继续处理
     */
    @Override
    public PersonResolveResult resolve(PersonResolveRequest request) {
        String initiator = request.initiatorId();
        if (!StringUtils.hasText(initiator)) {
            Object variable = request.variables().get("startUserId");
            if (variable == null) {
                variable = request.variables().get("submitterId");
            }
            initiator = variable == null ? null : String.valueOf(variable);
        }
        return StringUtils.hasText(initiator)
                ? PersonResolveResult.users(java.util.List.of(initiator))
                : new PersonResolveResult(
                        java.util.List.of(),
                        java.util.List.of("当前流程没有可识别的发起人"));
    }
}
