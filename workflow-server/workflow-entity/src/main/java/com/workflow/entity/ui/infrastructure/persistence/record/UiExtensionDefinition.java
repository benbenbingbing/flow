package com.workflow.entity.ui.infrastructure.persistence.record;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import org.springframework.util.StringUtils;

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

    /** 范围文档只有字符串数组，独立编解码器避免依赖应用层或数据库 JSON 方言。 */
    private static final JsonDocumentCodec SCOPE_CODEC = new JsonDocumentCodec(new ObjectMapper());

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
    /** 接口作用域：GLOBAL（全部实体）、ENTITY（选择实体）；FORM/LIST 仅保留历史记录。 */
    private String scopeType;
    /** 历史单一范围对象 ID，仅用于旧记录和不可变发布快照的兼容读取。 */
    private String scopeId;
    /** ENTITY 范围下的实体 ID 数组 JSON；用于候选、发布和执行时按实体匹配。 */
    private String scopeEntityIdsDocument;

    /**
     * 返回规范化的实体范围；旧 ENTITY 记录没有数组文档时才回退到 scopeId。
     * 历史 FORM/LIST 不转换为实体，避免旧发布接口自动扩大到同实体的其他页面。
     *
     * @return 去重、排序后的实体 ID；GLOBAL 或历史页面范围返回空列表
     * @throws IllegalArgumentException 范围文档不是非空字符串组成的数组时抛出
     */
    public List<String> getScopeEntityIds() {
        if (!"ENTITY".equalsIgnoreCase(scopeType)) {
            return List.of();
        }
        if (!StringUtils.hasText(scopeEntityIdsDocument)) {
            return StringUtils.hasText(scopeId) ? List.of(scopeId.trim()) : List.of();
        }
        return normalizeScopeEntityIds(SCOPE_CODEC.readArray(scopeEntityIdsDocument, "接口作用实体"));
    }

    /**
     * 保存实体集合的稳定表示；后续发布哈希不受选择顺序或重复 ID 影响。
     * @param entityIds 实体 ID 集合，null 表示空集合；ENTITY 的非空约束由保存服务校验
     * @throws IllegalArgumentException 包含空白 ID 时抛出
     */
    public void setScopeEntityIds(List<String> entityIds) {
        scopeEntityIdsDocument = SCOPE_CODEC.write(normalizeScopeEntityIds(entityIds), "接口作用实体");
    }

    /**
     * 规范化输入并拒绝空白或非字符串 ID，避免损坏范围被解释为全部实体。
     * @param entityIds 请求或持久化文档中的实体 ID 数组
     * @return 去重并按 ID 排序的不可变集合
     * @throws IllegalArgumentException 存在非字符串或空白元素时抛出
     */
    public static List<String> normalizeScopeEntityIds(List<?> entityIds) {
        TreeSet<String> result = new TreeSet<>();
        if (entityIds != null) {
            for (Object id : entityIds) {
                if (!(id instanceof String text) || !StringUtils.hasText(text)) {
                    throw new IllegalArgumentException("接口作用实体必须为非空实体 ID");
                }
                result.add(text.trim());
            }
        }
        return List.copyOf(result);
    }

    /**
     * 按真实所属实体匹配接口；业务上下文由调用方单独校验。
     * 历史页面范围继续精确匹配页面 ID，保证旧快照的权限边界不变。
     * @param entityId 经服务端确认的页面所属实体，不能使用客户端自报的归属
     * @param ownerType 当前宿主类型，历史范围需要与 FORM/LIST 精确一致
     * @param ownerId 当前宿主 ID
     * @return 该接口是否覆盖当前宿主；未知范围或缺失实体时不会授权
     */
    public boolean matchesScope(String entityId, String ownerType, String ownerId) {
        return switch (scopeType == null ? "" : scopeType.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "GLOBAL" -> true;
            case "ENTITY" -> entityId != null && getScopeEntityIds().contains(entityId);
            case "FORM", "LIST" -> scopeType.equalsIgnoreCase(ownerType)
                    && scopeId != null && scopeId.equals(ownerId);
            default -> false;
        };
    }

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

    /**
     * @return 接口扩展当前是否可执行
     *
     * @return 接口活动条件成立时为 true，否则为 false
     */
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
    /**
     * 读取来源编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的来源编码文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getSourceCode() {
        return extensionKey;
    }

    /**
     * 设置来源编码；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置来源编码的原始输入，结果供调用方继续使用
     */
    public void setSourceCode(String value) {
        extensionKey = value;
    }

    /**
     * 读取来源名称；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的来源名称文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getSourceName() {
        return displayName;
    }

    /**
     * 设置来源名称；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置来源名称的原始输入，结果供调用方继续使用
     */
    public void setSourceName(String value) {
        displayName = value;
    }

    /**
     * 读取来源类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的来源类型文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getSourceType() {
        return implementationType;
    }

    /**
     * 设置来源类型；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置来源类型的原始输入，结果供调用方继续使用
     */
    public void setSourceType(String value) {
        implementationType = value;
    }

    /**
     * 读取配置文档；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的配置文档文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getConfigDocument() {
        return implementationConfigDocument;
    }

    /**
     * 设置配置文档；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置配置文档的原始输入，结果供调用方继续使用
     */
    public void setConfigDocument(String value) {
        implementationConfigDocument = value;
    }

    /**
     * 读取操作输入结构文档；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的操作输入结构文档文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationInputSchemaDocument() {
        return inputSchemaDocument;
    }

    /**
     * 设置操作输入结构文档；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作输入结构文档的原始输入，结果供调用方继续使用
     */
    public void setOperationInputSchemaDocument(String value) {
        inputSchemaDocument = value;
    }

    /**
     * 读取操作输出结构文档；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的操作输出结构文档文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationOutputSchemaDocument() {
        return outputSchemaDocument;
    }

    /**
     * 设置操作输出结构文档；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作输出结构文档的原始输入，结果供调用方继续使用
     */
    public void setOperationOutputSchemaDocument(String value) {
        outputSchemaDocument = value;
    }

    /**
     * 读取操作编码；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的操作编码文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationCode() {
        return providerOperationCode;
    }

    /**
     * 设置操作编码；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作编码的原始输入，结果供调用方继续使用
     */
    public void setOperationCode(String value) {
        providerOperationCode = value;
    }

    /**
     * 读取操作上下文类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的操作上下文类型文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationContextType() {
        return interfaceContextType;
    }

    /**
     * 设置操作上下文类型；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作上下文类型的原始输入，结果供调用方继续使用
     */
    public void setOperationContextType(String value) {
        interfaceContextType = value;
    }

    /**
     * 读取操作类型；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的操作类型文本，供调用方比较或展示
     */
    @JsonIgnore
    public String getOperationKind() {
        return interfaceKind;
    }

    /**
     * 设置操作类型；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置操作类型的原始输入，结果供调用方继续使用
     */
    public void setOperationKind(String value) {
        interfaceKind = value;
    }

    /**
     * 读取启用；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的界面扩展定义结果，供调用方继续处理
     */
    @JsonIgnore
    public Boolean getEnabled() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * 设置启用；后续读取或执行将使用更新后的状态。
     *
     * @param value 待设置启用的原始输入，结果供调用方继续使用
     */
    public void setEnabled(Boolean value) {
        status = Boolean.FALSE.equals(value) ? "DISABLED" : "ACTIVE";
    }
}
