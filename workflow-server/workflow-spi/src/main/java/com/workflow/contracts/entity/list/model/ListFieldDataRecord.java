package com.workflow.contracts.entity.list.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 已授权、已分页的列表行快照，供扩展读取基础字段并计算展示数据。
 * 不包含 HTTP 令牌、按钮能力或写入指令；宿主只回写 data/extData，不接受身份、状态或行数变更。
 */
@Data
public class ListFieldDataRecord {
    private String id;
    private String entityCode;
    private String entityName;
    private String name;
    private String code;
    private String status;
    private String processStatus;
    private String processInstanceId;
    private LocalDateTime processStartTime;
    private LocalDateTime processEndTime;
    private String currentTaskId;
    private String currentTaskName;
    private String currentTaskAssignee;
    /** 扩展计算使用的业务值；修改只影响当前响应，不会持久化。 */
    private Map<String, Object> data;
    private String submitterId;
    private String submitterName;
    private String deptId;
    private String deptName;
    private LocalDateTime submitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Boolean deleted;
    /** 自定义列展示结果；宿主在扩展成功后合并回当前行。 */
    private Map<String, Object> extData;
}
