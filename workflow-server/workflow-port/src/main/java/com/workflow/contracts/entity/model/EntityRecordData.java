package com.workflow.contracts.entity.model;

import lombok.Data;
import java.util.Map;

/**
 * 业务编排使用的实体记录投影。只描述业务数据，不携带 HTTP 表单令牌、按钮能力或持久化注解。
 * 写入仍须经过 EntityMutationPort，本对象的修改不会自动保存。
 */
@Data
public class EntityRecordData {
    /** 记录定位信息，用于后续受控查询和变更。 */
    private String id;
    private String entityCode;
    private String name;
    private String code;
    /** 实体业务状态，与流程生命周期独立。 */
    private String status;
    private String processStatus;
    private String processInstanceId;
    /** 按发布字段编码组织的业务值，供动作规则读取或构建变更载荷。 */
    private Map<String, Object> data;
    /** 发起人与部门快照，业务动作创建关联记录时沿用。 */
    private String submitterId;
    private String submitterName;
    private String deptId;
    /** 创建者标识供规则求值使用；不包含认证凭据。 */
    private String createBy;
    /** 自定义列表字段的计算结果，与实际业务字段 data 隔离。 */
    private Map<String, Object> extData;
}
