package com.workflow.process.assignment.extension;

import com.workflow.contracts.extension.ExtensionImplementationOrigin;
import com.workflow.contracts.identity.model.GroupMemberFilter;
import com.workflow.contracts.identity.port.GroupMemberTagPort;
import com.workflow.contracts.process.assignment.model.*;
import com.workflow.contracts.process.assignment.spi.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/** 设计器的“用户组+标签”统一转换为此解析器，普通审批、会签、预览及提交重验共用。 */
@Component
@RequiredArgsConstructor
public class GroupMembersByTagPersonResolver implements PersonResolverProvider, PersonResolverConfigurationValidationProvider {
    public static final String CODE = "groupMembersByTag";
    private final GroupMemberTagPort directory;

    @Override public ExtensionImplementationOrigin implementationOrigin() { return ExtensionImplementationOrigin.PLATFORM; }
    @Override public String resolverCode() { return CODE; }
    @Override public PersonResolverDescriptor descriptor() {
        return new PersonResolverDescriptor(CODE, "用户组成员标签", "按组内标签筛选成员；空标签表示全组，多标签按或匹配。", 1, 1,
                Set.of(PersonResolveUsage.ASSIGNEE, PersonResolveUsage.CANDIDATE, PersonResolveUsage.MULTI_INSTANCE), Map.of(), false);
    }

    /** 发布时同时验证参数形状、组状态及标签归属，拒绝伪造或已失效条件。 */
    @Override public void validate(PersonResolverConfigurationValidationRequest request) {
        if (!request.multiInstance() && !"CANDIDATE".equals(request.assignmentMode())) {
            throw new IllegalArgumentException("用户组标签普通审批必须使用候选人模式");
        }
        directory.validate(filters(request.extraParams()));
    }

    /** 每次解析读取当前目录；任务落地后由引擎保存候选用户，不保留可绕过标签的整组候选关系。 */
    @Override public PersonResolveResult resolve(PersonResolveRequest request) {
        try {
            return PersonResolveResult.users(directory.resolve(filters(request.extraParams())));
        } catch (IllegalArgumentException error) {
            throw new com.workflow.contracts.process.assignment.error.PersonResolutionException("GROUP_TAG_SCOPE_INVALID", error.getMessage());
        }
    }

    /** 不吞掉非法参数，否则错误标签可能变成空标签并扩大到全组。 */
    static List<GroupMemberFilter> filters(Map<String, Object> params) {
        if (!(params.get("groups") instanceof List<?> rows) || rows.isEmpty() || rows.size() > 100) throw new IllegalArgumentException("用户组标签配置缺少有效 groups");
        List<GroupMemberFilter> filters = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map) || !(map.get("groupCode") instanceof String code)
                    || !(map.get("tagIds") instanceof List<?> ids) || ids.stream().anyMatch(id -> !(id instanceof String))) {
                throw new IllegalArgumentException("用户组标签配置格式非法");
            }
            var filter = new GroupMemberFilter(code, ids.stream().map(String.class::cast).toList());
            if (!codes.add(filter.groupCode())) throw new IllegalArgumentException("用户组标签配置重复");
            filters.add(filter);
        }
        return List.copyOf(filters);
    }
}
