package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 表单字段配置
 */
@Data
@TableName("entity_form_field")
public class EntityFormField {

    @TableId(type = IdType.ASSIGN_ID)
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
     * 字段编码（对应entity_field的field_code），持久化到数据库，用于前端表单字段 key
     */
    @com.baomidou.mybatisplus.annotation.TableField("field_code")
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
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String optionsJson;

    /**
     * 关联实体ID（用于引用实体字段，非数据库字段，从entity_field补充）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String refEntityId;

    /**
     * 引用实体类型（用于引用实体字段，非数据库字段，从entity_field补充）
     * CUSTOM - 用户自定义实体
     * USER - 系统用户
     * DEPT - 系统部门
     * ROLE - 系统角色
     * GROUP - 系统用户组
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String refEntityType;

    /**
     * 子表单显示方式（embedded/tab），非数据库字段，从entity_field补充
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String displayMode;

    /**
     * 子表单关联字段编码，非数据库字段，从entity_field补充
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String refFieldCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String relationCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String relationName;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String childEntityId;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String childEntityCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String childRefFieldCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String relationType;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean cascadeDelete;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Boolean relationRequired;

    /**
     * 栅格宽度（1-24）
     */
    private Integer gridSpan;

    /**
     * 创建时间
     */
    @com.baomidou.mybatisplus.annotation.TableField("created_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @com.baomidou.mybatisplus.annotation.TableField("updated_time")
    private LocalDateTime updateTime;
}
