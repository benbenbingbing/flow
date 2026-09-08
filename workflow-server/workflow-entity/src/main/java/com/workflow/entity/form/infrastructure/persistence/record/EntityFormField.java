package com.workflow.entity.form.infrastructure.persistence.record;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 表单字段运行视图，由节点配置或不可变发布快照生成，不映射数据库表。
 */
@Data
public class EntityFormField {

    /** 主键ID */
    private String id;

    /**
     * 表单ID
     */
    private String formId;

    /**
     * 字段ID（对应entity_field）
     */
    private String fieldId;

    /**
     * 字段编码（对应entity_field的field_code），用于前端表单字段 key
     */
    private String fieldCode;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 显示标签
     */
    private String fieldLabel;

    /**
     * 字段类型
     */
    private String fieldType;

    /**
     * 排序
     */
    private Integer sortOrder;

    /**
     * 是否必填：0-否 1-是
     */
    private Integer isRequired;

    /**
     * 是否只读：0-否 1-是
     */
    private Integer isReadonly;

    /**
     * 是否隐藏：0-否 1-是
     */
    private Integer isHidden;

    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 占位提示
     */
    private String placeholder;

    /**
     * 校验规则JSON
     */
    private String validationRules;

    /**
     * 字段模式权限及扩展配置JSON
     */
    private String extensionConfig;

    /**
     * 组件类型：input/select/date/number等
     */
    private String componentType;

    /**
     * 组件额外配置JSON
     */
    private String componentProps;

    /**
     * 选项配置JSON（用于下拉、单选、多选，非数据库字段，从entity_field补充）
     */
    private String optionsJson;

    /**
     * 关联实体ID（用于引用实体字段，非数据库字段，从entity_field补充）
     */
    private String refEntityId;

    /**
     * 引用实体类型（用于引用实体字段，非数据库字段，从entity_field补充）
     * CUSTOM - 用户自定义实体
     * USER - 系统用户
     * DEPT - 系统部门
     * ROLE - 系统角色
     * GROUP - 系统用户组
     */
    private String refEntityType;

    /**
     * 子表单关联字段编码，非数据库字段，从entity_field补充
     */
    private String refFieldCode;

    /** 子列表或实体引用默认列表编码（非数据库字段，从 entity_field 补充） */
    private String refListKey;

    private String relationCode;

    /** 关系名称（非数据库字段，从entity_field补充） */
    private String relationName;

    /** 子实体ID（非数据库字段，从entity_field补充） */
    private String childEntityId;

    /** 子实体编码（非数据库字段，从entity_field补充） */
    private String childEntityCode;

    /** 子表单关联字段编码（非数据库字段，从entity_field补充） */
    private String childRefFieldCode;

    /** 关系类型（ONE_TO_ONE/ONE_TO_MANY，非数据库字段，从entity_field补充） */
    private String relationType;

    /** 是否级联删除（非数据库字段，从entity_field补充） */
    private Boolean cascadeDelete;

    /** 关系是否必填（非数据库字段，从entity_field补充） */
    private Boolean relationRequired;

    /** 数据源绑定配置（非数据库字段，从entity_field补充） */
    private Map<String, Object> dataSourceBindings;

    /**
     * 栅格宽度（1-24）
     */
    private Integer gridSpan;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
