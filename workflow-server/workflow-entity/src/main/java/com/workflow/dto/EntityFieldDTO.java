package com.workflow.dto;

import com.workflow.entity.EntityField;
import com.workflow.entity.EntityFieldFileItem;
import lombok.Data;

import java.util.List;

/**
 * 实体字段DTO
 */
@Data
public class EntityFieldDTO {
    private String id;
    private String fieldCode;
    private String fieldName;
    private EntityField.FieldType fieldType;
    private String dbType;
    private Integer fieldLength;
    private Integer fieldPrecision;
    private String dbColumnName;
    private Boolean isRequired;
    private Boolean isUnique;
    private String defaultValue;
    private String optionsJson;
    private String validateRules;
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
    private String relationCode;
    private String relationName;
    private String childEntityId;
    private String childEntityCode;
    private String childRefFieldCode;
    private String relationType;    // ONE_TO_ONE/ONE_TO_MANY
    private Boolean cascadeDelete;
    private Boolean relationRequired;
    
    // 文件字段多组附件配置
    private List<EntityFieldFileItem> fileItems;
}
