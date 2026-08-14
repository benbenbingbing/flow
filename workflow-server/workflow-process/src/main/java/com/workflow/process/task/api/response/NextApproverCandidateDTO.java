package com.workflow.process.task.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可展示的最小审批人信息，不暴露用户敏感字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NextApproverCandidateDTO {

    private String userId;
    private String username;
    private String displayName;
}
