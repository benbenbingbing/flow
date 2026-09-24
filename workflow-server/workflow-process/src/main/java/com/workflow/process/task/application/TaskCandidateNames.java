package com.workflow.process.task.application;

import com.workflow.contracts.identity.port.IdentityDirectoryPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** 任务初始镜像与最终投影共用的候选组展示名解析，不赋予任何办理权限。 */
final class TaskCandidateNames {
    private TaskCandidateNames() {}

    /** 保留组不存在/无成员时的名称回退；目录异常由创建或投影入口按原策略处理。 */
    static List<String> groupMembers(IdentityDirectoryPort directory, String groupCode) {
        var group = directory.findGroup(groupCode).orElse(null);
        if (group == null) return Collections.singletonList(groupCode);
        var users = directory.findGroupUsers(group.id());
        // 无成员且无组名时交给调用方回退到组编码，不能把 null 拼成字面 "null"。
        if (users.isEmpty()) return group.name() == null ? List.of() : List.of(group.name());
        var names = new LinkedHashSet<String>();
        for (var user : users) names.add(directory.getDisplayName(user.id()));
        return new ArrayList<>(names);
    }
}
