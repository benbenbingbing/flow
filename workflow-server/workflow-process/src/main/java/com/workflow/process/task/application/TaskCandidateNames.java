package com.workflow.process.task.application;

import com.workflow.port.system.SysGroupPort;
import com.workflow.port.system.SysUserGroupPort;

import com.workflow.port.system.SysUserLookupPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** 任务初始镜像与最终投影共用的候选组展示名解析，不赋予任何办理权限。 */
final class TaskCandidateNames {
    private TaskCandidateNames() {}

    /** 保留组不存在/无成员时的名称回退；目录异常由创建或投影入口按原策略处理。 */
    static List<String> groupMembers(SysGroupPort groups, SysUserGroupPort memberships,
            SysUserLookupPort users, String groupCode) {
        var group = groups.findGroup(groupCode).orElse(null);
        if (group == null) return Collections.singletonList(groupCode);
        var members = memberships.findGroupUsers(group.id());
        // 无成员且无组名时交给调用方回退到组编码，不能把 null 拼成字面 "null"。
        if (members.isEmpty()) return group.name() == null ? List.of() : List.of(group.name());
        var names = new LinkedHashSet<String>();
        for (var user : members) names.add(users.getDisplayName(user.id()));
        return new ArrayList<>(names);
    }
}
