package com.workflow.biz.project.zdw;

import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import com.workflow.contracts.process.assignment.spi.PersonResolver;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 流程中自定义用户信息
 */
@Component("ZdwCustomPerson")
public class ZdwCustomPerson implements PersonResolver {

    @Override
    public PersonResolverDescriptor descriptor() {
        return new PersonResolverDescriptor("ZdwCustomPerson",
                "ZDW自定义人员解析器",
                "ZDW自定义人员解析器描述",
                1,
                1,
                Set.of(PersonResolveUsage.ASSIGNEE),
                null,
                false);
    }

    @Override
    public PersonResolveResult resolve(PersonResolveRequest request) {
        return PersonResolveResult.users(List.of("lisi"));
    }
}
