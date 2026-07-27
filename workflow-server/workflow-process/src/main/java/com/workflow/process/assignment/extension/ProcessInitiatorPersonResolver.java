package com.workflow.process.assignment.extension;

import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
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

    @Override
    public PersonResolverDescriptor descriptor() {
        return DESCRIPTOR;
    }

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
