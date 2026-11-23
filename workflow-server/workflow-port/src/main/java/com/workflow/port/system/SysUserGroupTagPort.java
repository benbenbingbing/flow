package com.workflow.port.system;

import com.workflow.port.system.identity.model.GroupMemberFilter;
import java.util.List;

/** 身份模块提供组内标签查询；流程模块不得直接读取成员或标签持久化表。 */
public interface SysUserGroupTagPort {
    /** 校验组已启用、标签均属于该组且仍存在；失效条件必须报错，不能降级成全组。 */
    void validateTags(List<GroupMemberFilter> filters);
    /** 展开当前有效成员，组间合并去重；空匹配由流程的空办理人策略处理。 */
    List<String> resolveMembersByTags(List<GroupMemberFilter> filters);
}
