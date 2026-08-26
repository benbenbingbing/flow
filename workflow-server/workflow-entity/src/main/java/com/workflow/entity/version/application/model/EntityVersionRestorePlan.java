package com.workflow.entity.version.application.model;

import java.util.List;

/**
 * 历史业务版本恢复的只读预演结果。
 *
 * <p>该模型只描述差异和阻断项，不是可执行命令。当前服务端没有恢复写入口，
 * 调用方不得把 {@link Action} 直接转发给实体变更执行链。</p>
 */
public record EntityVersionRestorePlan(
        int schemaVersion,
        String entityCode,
        String recordId,
        Integer targetVersionNo,
        boolean executable,
        Summary summary,
        List<Action> actions,
        List<Blocker> blockers) {

    public EntityVersionRestorePlan {
        actions = actions == null ? List.of() : List.copyOf(actions);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    /** 一项只读恢复意图；operation 仅允许 CREATE/UPDATE/DELETE/LINK/UNLINK。 */
    public record Action(
            String operation,
            String nodeCode,
            String entityCode,
            String recordId,
            String parentRecordId,
            List<String> changedFieldCodes,
            String description) {

        public Action {
            changedFieldCodes = changedFieldCodes == null
                    ? List.of() : List.copyOf(changedFieldCodes);
        }
    }

    /** 阻断恢复执行的安全、权限、流程或版本问题。 */
    public record Blocker(
            String code,
            String nodeCode,
            String entityCode,
            String recordId,
            String message) {
    }

    public record Summary(
            int createCount,
            int updateCount,
            int deleteCount,
            int linkCount,
            int unlinkCount,
            int blockerCount) {
    }
}
