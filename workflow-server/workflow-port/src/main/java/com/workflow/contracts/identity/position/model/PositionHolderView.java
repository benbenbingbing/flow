package com.workflow.contracts.identity.position.model;

/** 当前职务人员的最小投影，供流程生成办理人；不携带任职时间或主职优先级。 */
public record PositionHolderView(String userId, String username, String displayName) {
    /** 用户 ID 关联目录，username 用作流程人员标识，二者均必须存在。 */
    public PositionHolderView {
        if (userId == null || userId.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("职务人员 ID 和用户名不能为空");
        }
    }
}
