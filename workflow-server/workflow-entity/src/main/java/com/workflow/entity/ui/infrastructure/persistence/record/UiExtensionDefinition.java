package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一扩展定义实体，对应 ui_extension_definition 表。
 *
 * <p>一条记录代表一个可以被设计器选择的完整能力。UI 组件继续使用组件兼容
 * 字段；{@code INTERFACE} 类型使用接口实现、上下文、读写类型与输入输出 Schema
 * 字段。接口不再包含可由前端二次选择的操作集合。</p>
 */
@Data
@TableName("ui_extension_definition")
public class UiExtensionDefinition {

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    /** 扩展类型（如 component-slot、action 等） */
    private String extensionType;
    /** 扩展唯一标识 */
    private String extensionKey;
    /** 扩展显示名称 */
    private String displayName;
    /** 当前版本号 */
    private Integer version;
    /** 当前激活的发布快照版本号 */
    private Integer snapshotVersion;
    /** 适用范围：GLOBAL 表示全部实体，ENTITY 表示指定实体 */
    private String visibilityScope;
    /** 指定适用实体编码列表（JSON） */
    private String entityCodesDocument;
    /** 支持的表单模式列表（JSON，如 view/edit） */
    private String supportedModesDocument;
    /** 支持的节点类型列表（JSON） */
    private String supportedNodeTypesDocument;
    /** 支持的绑定来源列表（JSON） */
    private String supportedBindingsDocument;
    /** 扩展配置项 Schema（JSON） */
    private String configSchemaDocument;
    /** 扩展能力声明（JSON） */
    private String capabilitiesDocument;
    /** 接口实现类型，例如 STATIC_OPTIONS 或 REGISTERED_PROVIDER。 */
    private String implementationType;
    /** Provider 实现编码；非 Provider 接口可为空。 */
    private String providerCode;
    /** 宿主发布时固定的 Provider 版本（仅钉版运行时）。 */
    @TableField(exist = false)
    private Integer providerVersion;
    /** 宿主发布时固定的 Provider 制品摘要（仅钉版运行时）。 */
    @TableField(exist = false)
    private String providerArtifactDigest;
    /** 接口作用域：GLOBAL、ENTITY、FORM 或 LIST。 */
    private String scopeType;
    /** 非 GLOBAL 作用域对应的实体、表单或列表主键 ID。 */
    private String scopeId;
    /** 接口实现配置 JSON。 */
    private String implementationConfigDocument;
    /** 接口执行策略 JSON，例如缓存、超时和幂等策略。 */
    private String executionPolicyDocument;
    /** 接口输入 Schema JSON。 */
    private String inputSchemaDocument;
    /** 接口输出 Schema JSON。 */
    private String outputSchemaDocument;
    /** 接口读写类型：READ 或 WRITE。 */
    private String interfaceKind;
    /** 接口调用上下文：FORM、LIST 或 ENTITY。 */
    private String interfaceContextType;
    /**
     * Provider 内部方法路由编码。
     *
     * <p>该字段只用于调用实现及兼容旧发布快照，不能作为设计器的第二层选择。</p>
     */
    private String providerOperationCode;
    /** 迁移前接口服务 ID，仅用于解析不可变历史快照。 */
    @JsonIgnore
    private String legacyServiceId;
    /** 状态（如 DRAFT/PUBLISHED/DISABLED） */
    private String status;
    /** 草稿元数据修订号 */
    private Integer revision;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("update_time")
    private LocalDateTime updatedAt;

    /** 逻辑删除标志（0-未删除 1-已删除） */
    @TableLogic
    private Integer deleted;

    /** @return 接口扩展当前是否可执行 */
    public boolean isInterfaceActive() {
        return "INTERFACE".equalsIgnoreCase(extensionType)
                && "ACTIVE".equalsIgnoreCase(status)
                && Integer.valueOf(0).equals(deleted);
    }

    /*
     * 以下别名只服务于历史发布快照的执行代码。数据库查询与新接口契约必须使用
     * extension/implementation/interface 命名，避免旧的“服务 + 操作”模型重新
     * 泄漏到设计器。
     */
    @JsonIgnore
    public String getSourceCode() {
        return extensionKey;
    }

    public void setSourceCode(String value) {
        extensionKey = value;
    }

    @JsonIgnore
    public String getSourceName() {
        return displayName;
    }

    public void setSourceName(String value) {
        displayName = value;
    }

    @JsonIgnore
    public String getSourceType() {
        return implementationType;
    }

    public void setSourceType(String value) {
        implementationType = value;
    }

    @JsonIgnore
    public String getConfigDocument() {
        return implementationConfigDocument;
    }

    public void setConfigDocument(String value) {
        implementationConfigDocument = value;
    }

    @JsonIgnore
    public String getOperationInputSchemaDocument() {
        return inputSchemaDocument;
    }

    public void setOperationInputSchemaDocument(String value) {
        inputSchemaDocument = value;
    }

    @JsonIgnore
    public String getOperationOutputSchemaDocument() {
        return outputSchemaDocument;
    }

    public void setOperationOutputSchemaDocument(String value) {
        outputSchemaDocument = value;
    }

    @JsonIgnore
    public String getOperationCode() {
        return providerOperationCode;
    }

    public void setOperationCode(String value) {
        providerOperationCode = value;
    }

    @JsonIgnore
    public String getOperationContextType() {
        return interfaceContextType;
    }

    public void setOperationContextType(String value) {
        interfaceContextType = value;
    }

    @JsonIgnore
    public String getOperationKind() {
        return interfaceKind;
    }

    public void setOperationKind(String value) {
        interfaceKind = value;
    }

    @JsonIgnore
    public Boolean getEnabled() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public void setEnabled(Boolean value) {
        status = Boolean.FALSE.equals(value) ? "DISABLED" : "ACTIVE";
    }
}
