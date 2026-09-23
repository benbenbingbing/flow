package com.workflow.biz.project.zdw;

import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveResult;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.contracts.process.assignment.model.PersonResolverDescriptor;
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

    /**
     * 处理描述，并将结果传给后续步骤。
     *
     * @return 处理后的描述结果，供调用方继续处理
     */
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

    /**
     * 解析{@code zdw}自定义人员；输出作为后续校验或处理的输入。
     *
     * @param request 本次请求，后续经校验后用于解析{@code zdw}自定义人员
     * @return 解析后的{@code zdw}自定义人员结果，供调用方继续处理
     */
    @Override
    public PersonResolveResult resolve(PersonResolveRequest request) {
        return PersonResolveResult.users(List.of("lisi"));
    }
}
