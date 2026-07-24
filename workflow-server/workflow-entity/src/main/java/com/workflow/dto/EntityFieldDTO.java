package com.workflow.dto;

import com.workflow.entity.EntityField;
import com.workflow.entity.EntityFieldFileItem;
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
    /** 选项 JSON */
    private String optionsJson;

    /** 选项列表（结构化形式） */
    private List<Map<String, Object>> options;
    /** 字典类型 */
    private String dictType;
    /** 值存储方式 */
    private String valueStorage;
    /** 验证规则 */
    private String validateRules;
    /** 排序序号 */
    private Integer sortOrder;
    private Boolean isSystem;       // 是否系统字段
    private Boolean editable;       // 是否可编辑
    private Boolean isPublished;    // 是否已发布到数据库表
    private String fileTypes;       // 文件类型限制（用于附件类型）
    private Integer fileMaxSize;    // 文件大小限制（MB，用于附件类型）
    private Integer fileMaxCount;   // 文件数量限制（用于附件类型）
    
    // 实体引用/子表单相关字段
    private String refEntityId;     // 关联实体ID
    private String refEntityType;   // 引用实体类型（CUSTOM/USER/DEPT/ROLE/GROUP）
    private String displayMode;     // 显示方式（embedded/tab）
    private String refFieldCode;    // 关联字段编码

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
