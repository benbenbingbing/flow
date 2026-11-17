package com.workflow.process.action.infrastructure.persistence.record;

import lombok.Data;

/** 发布版本的动作数量查询投影，供版本历史批量装配统计值，不对应数据库表。 */
@Data
public class FlowActionVersionCount {
    /** 发布版本 ID，用于把聚合结果关联回版本历史记录。 */
    private String versionId;

    /** 与版本动作明细一致的记录数，包括 enabled=false 的已发布动作。 */
    private long actionCount;
}
