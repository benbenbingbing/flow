package com.workflow.contracts.entity.port;

import com.workflow.contracts.entity.model.EntityTaskSummary;

/** 流程投影/回填专用内部读取端口；调用方先确定业务记录关联，不授予外部数据读取权限。 */
public interface EntityTaskSummaryPort {
    /** 读取列表固定字段；记录或实体不存在时返回空摘要，不递归读取业务子表。 */
    EntityTaskSummary findTaskSummary(String entityCode, String recordId);
}
