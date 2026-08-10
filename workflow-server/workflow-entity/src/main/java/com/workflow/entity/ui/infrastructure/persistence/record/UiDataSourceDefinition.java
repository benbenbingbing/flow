package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 接口服务定义实体，对应 ui_data_source_definition 表。
 *
 * <p>声明服务实现、可见作用域、操作集合和执行策略；每个操作独立声明
 * 上下文类型、读写类型及输入输出 Schema。</p>
 */
@Data
@TableName("ui_data_source_definition")
public class UiDataSourceDefinition {

    /** 接口服务主键 ID。 */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 接口服务唯一编码。 */
    private String sourceCode;
    /** 接口服务显示名称。 */
    private String sourceName;
    /** 服务实现类型，例如 STATIC_OPTIONS、PROVIDER、INTEGRATION_CONNECTOR。 */
    private String sourceType;
    /** Provider 实现编码；非 Provider 类型可为空。 */
    private String providerCode;
    /** 可见作用域类型：GLOBAL、ENTITY、FORM 或 LIST。 */
    private String scopeType;
    /** 非 GLOBAL 作用域对应的实体、表单或列表主键 ID。 */
    private String scopeId;
    /** 服务实现配置 JSON。 */
    private String configDocument;
    /** 执行策略配置 JSON，例如缓存、超时和幂等策略。 */
    private String executionPolicyDocument;
    /** 接口服务操作定义 JSON 数组。 */
    private String operationsDocument;
    /** 当前解析出的操作输入 Schema（仅运行时） */
    @TableField(exist = false)
    private String operationInputSchemaDocument;
    /** 当前解析出的操作输出 Schema（仅运行时） */
    @TableField(exist = false)
    private String operationOutputSchemaDocument;
    /** 当前解析出的操作编码（仅运行时） */
    @TableField(exist = false)
    private String operationCode;
    /** 当前解析出的操作上下文类型（仅运行时） */
    @TableField(exist = false)
    private String operationContextType;
    /** 当前解析出的操作读写类型（仅运行时） */
    @TableField(exist = false)
    private String operationKind;
    /** 草稿元数据修订号，用于乐观并发控制。 */
    private Integer revision;
    /** 接口服务是否启用。 */
    private Boolean enabled;

    /** 创建时间。 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志：0 未删除，1 已删除。 */
    @TableLogic
    private Integer deleted;
}
