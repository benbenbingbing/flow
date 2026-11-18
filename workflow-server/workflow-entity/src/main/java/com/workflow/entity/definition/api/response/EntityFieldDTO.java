package com.workflow.entity.definition.api.response;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 实体字段DTO
 */
@Data
public class EntityFieldDTO {
    /** 字段 ID */
    private String id;
    /** 字段编码 */
    private String fieldCode;
    /** 字段名称 */
    private String fieldName;
    /** 字段类型 */
    private EntityField.FieldType fieldType;
    /** 数据库类型 */
    private String dbType;
    /** 字段长度 */
    private Integer fieldLength;
    /** 字段精度（小数位） */
    private Integer fieldPrecision;
    /** 数据库列名（下划线命名） */
    private String dbColumnName;
    /** 是否必填 */
    private Boolean isRequired;
    /** 是否唯一 */
    private Boolean isUnique;
    /** 默认值 */
    private String defaultValue;
    /** 仅接收用于拒绝旧客户端配置；不再输出或持久化内嵌选项。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String optionsJson;

    /** 仅接收用于拒绝旧结构化选项，选项只能由代码表在运行时解析。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private List<Map<String, Object>> options;
    /** 字典类型 */
    private String dictType;

    /** 已注册的只读选项接口；仅供表单取候选，不在实体提交时调用。 */
    private String optionsInterfaceId;
    /** 值存储方式 */
    private String valueStorage;
    /** 验证规则 */
    private String validateRules;
    /** 排序序号 */
    private Integer sortOrder;
    private Boolean isSystem;       // 是否系统字段
    private Boolean editable;       // 是否可编辑
    private Boolean isPublished;    // 是否已发布到数据库表
    private Boolean uiConfigurable; // 是否允许加入表单或列表配置
    private Boolean runtimeReadable;// 通用运行时是否允许读取
    private String fileTypes;       // 文件类型限制（用于附件类型）
    private Integer fileMaxSize;    // 文件大小限制（MB，用于附件类型）
    private Integer fileMaxCount;   // 文件数量限制（用于附件类型）
    
    // 实体引用/子表单相关字段
    private String refEntityId;     // 关联实体ID
    private String refEntityType;   // 引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）
    private String refFieldCode;    // 关联字段编码
    private String refListKey;      // 子列表或实体引用默认列表编码

    // 子表单关系配置
    private String relationCode;          // 关系编码
    private String relationName;          // 关系名称
    private String childEntityId;         // 子实体ID
    private String childEntityCode;       // 子实体编码
    private String childRefFieldCode;     // 子实体引用字段编码（回溯父记录的字段）
    private String relationType;    // ONE_TO_ONE/ONE_TO_MANY
    private Boolean cascadeDelete;        // 是否级联删除
    private Boolean relationRequired;     // 关联是否必填
    
    // 文件字段多组附件配置
    private List<EntityFieldFileItem> fileItems;
}
