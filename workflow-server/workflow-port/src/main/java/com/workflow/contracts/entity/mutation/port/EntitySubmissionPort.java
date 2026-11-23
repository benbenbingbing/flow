package com.workflow.contracts.entity.mutation.port;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.mutation.model.EntitySubmissionCommand;

/** 实体的发布表单提交、事务写入与流程发起入口。 */
public interface EntitySubmissionPort {
    /** 在同一事务内提交；相同操作者以同一 requestId 重试时重放原结果。 */
    EntityRecordData submit(EntitySubmissionCommand command);
}
