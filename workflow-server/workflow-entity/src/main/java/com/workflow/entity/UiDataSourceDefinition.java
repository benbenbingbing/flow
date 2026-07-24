package com.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UI 数据源定义实体，对应 ui_data_source_definition 表。
 * 声明一个可供表单/列表挂载的数据源（如实体查询、字典、自定义提供者），
 * 包含作用范围、输入输出 Schema、执行策略等元数据。
 */
@Data
@TableName("ui_data_source_definition")
public class UiDataSourceDefinition {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 数据源唯一编码 */
    private String sourceCode;
    /** 数据源名称 */
    private String sourceName;
    /** 数据源类型（如 entity/dict/custom 等） */
    private String sourceType;
    /** 提供者编码（自定义数据源的处理逻辑标识） */
    private String providerCode;
    /** 作用范围类型（如 global/entity/list 等） */
    private String scopeType;
    /** 作用范围目标ID（如实体编码、列表key） */
    private String scopeId;
    /** 数据源配置（JSON） */
    private String configDocument;
    /** 输入参数 Schema（JSON） */
    private String inputSchemaDocument;
    /** 输出结果 Schema（JSON） */
    private String outputSchemaDocument;
    /** 执行策略配置（JSON，如缓存、分页等） */
    private String executionPolicyDocument;
    /** 草稿元数据修订号 */
    private Integer revision;
    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;
}
