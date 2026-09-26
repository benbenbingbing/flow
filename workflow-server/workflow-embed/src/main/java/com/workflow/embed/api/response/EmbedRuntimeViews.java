package com.workflow.embed.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stable external projections returned by the Embed Runtime read APIs. */
public final class EmbedRuntimeViews {

    /**
     * 初始化嵌入式运行时视图，保存构造参数供后续方法使用。
     */
    private EmbedRuntimeViews() {
    }

    /**
     * 封装初始化的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param session 会话，保存在对象中供后续校验、查询或展示
     * @param actor 操作人，保存在对象中供后续校验、查询或展示
     * @param view 视图，保存在对象中供后续校验、查询或展示
     * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
     * @param ui 界面，保存在对象中供后续校验、查询或展示
     * @param limits 限制集合，保存在对象中供后续校验、查询或展示
     * @param target 目标，保存在对象中供后续校验、查询或展示
     */
    public record Bootstrap(
            Session session,
            Actor actor,
            View view,
            List<String> capabilities,
            Ui ui,
            Limits limits,
            @JsonInclude(JsonInclude.Include.NON_NULL) Target target) {

        /**
         * 兼容 LIST Runtime 调用方；LIST 没有原生 FORM target。
         *
         * @param session 会话，保存在对象中供后续校验、查询或展示
         * @param actor 操作人，保存在对象中供后续校验、查询或展示
         * @param view 视图，保存在对象中供后续校验、查询或展示
         * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
         * @param ui 界面，保存在对象中供后续校验、查询或展示
         * @param limits 限制集合，保存在对象中供后续校验、查询或展示
         */
        public Bootstrap(
                Session session,
                Actor actor,
                View view,
                List<String> capabilities,
                Ui ui,
                Limits limits) {
            this(session, actor, view, capabilities, ui, limits, null);
        }
    }

    /**
     * 供 iframe 原生 Flow 页面启动的只读固定目标。
     *
     * <p>所有值均由 Session + immutable Release 恢复；浏览器只能消费，后续 API
     * 仍会在服务端重新比对。解析令牌仅授权同一发布快照及其声明的嵌套表单。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param formId 表单 ID，后续用于定位已发布表单
     * @param formReleaseId 表单发布版本ID，后续用于处理目标时定位或关联目标
     * @param formReleaseVersion 表单发布版本，保存在对象中供后续校验、查询或展示
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @param listReleaseId 列表发布版本ID，后续用于处理目标时定位或关联目标
     * @param listReleaseVersion 列表发布版本，保存在对象中供后续校验、查询或展示
     * @param entryMode 入口模式标识，决定后续目标采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param formReleaseResolutionToken 表单发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param defaultFormResolved 默认表单已解析，保存在对象中供后续校验、查询或展示
     * @param listReleaseResolutionToken 列表发布版本解析令牌，后续用于授权校验、关联或幂等去重
     * @param initialData 初始数据，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     * @param context 执行上下文，向后续目标步骤传递身份、配置或状态
     */
    public record Target(
            String entityCode,
            @JsonInclude(JsonInclude.Include.NON_NULL) String formId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String formReleaseId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer formReleaseVersion,
            @JsonInclude(JsonInclude.Include.NON_NULL) String listKey,
            @JsonInclude(JsonInclude.Include.NON_NULL) String listReleaseId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer listReleaseVersion,
            String entryMode,
            @JsonInclude(JsonInclude.Include.NON_NULL) String recordId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String processInstanceId,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String formReleaseResolutionToken,
            boolean defaultFormResolved,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String listReleaseResolutionToken,
            Map<String, Object> initialData,
            Map<String, Object> parameters,
            Map<String, Object> context) {
    }

    /**
     * 封装会话的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param expiresAt 过期时间，后续用于判断有效期或展示该事件的发生时间
     * @param idleExpiresAt 空闲过期时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record Session(String id, Instant expiresAt, Instant idleExpiresAt) {
    }

    /**
     * 只用于 iframe 内存中的原生 UI 显隐，不是授权凭据。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param nickname 用户昵称，供界面展示
     * @param displayName 用户可见名称，供界面和日志展示
     * @param roles 角色集合，保存在对象中供后续校验、查询或展示
     * @param isSuperAdmin 是否{@code super}{@code admin}，保存在对象中供后续校验、查询或展示
     * @param permissions {@code permissions}，保存在对象中供后续校验、查询或展示
     */
    public record Actor(
            String username,
            String nickname,
            String displayName,
            List<String> roles,
            boolean isSuperAdmin,
            List<String> permissions) {

        /**
         * 兼容 LIST 端的旧构造方式和单元测试。
         *
         * @param displayName 用户可见名称，供界面和日志展示
         */
        public Actor(String displayName) {
            this(displayName, displayName, displayName,
                    List.of(), false, List.of());
        }
    }

    /**
     * 封装视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param name 展示名称，供界面或日志识别
     * @param surfaceType 界面类型标识，决定后续视图采用的处理分支
     * @param entryMode 入口模式标识，决定后续视图采用的处理分支
     */
    public record View(
            String key,
            String name,
            String surfaceType,
            String entryMode) {
    }

    /**
     * 封装界面的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param locale {@code locale}，保存在对象中供后续校验、查询或展示
     * @param theme {@code theme}，保存在对象中供后续校验、查询或展示
     * @param formPresentation 表单展示，保存在对象中供后续校验、查询或展示
     * @param showSearch {@code show}{@code search}，保存在对象中供后续校验、查询或展示
     * @param showPagination {@code show}{@code pagination}，保存在对象中供后续校验、查询或展示
     * @param showToolbar {@code show}{@code toolbar}，保存在对象中供后续校验、查询或展示
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param heightMode {@code height}模式标识，决定后续界面采用的处理分支
     */
    public record Ui(
            String locale,
            String theme,
            String formPresentation,
            boolean showSearch,
            boolean showPagination,
            boolean showToolbar,
            int pageSize,
            String heightMode) {
    }

    /**
     * 封装限制集合的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param maxPageSize 最大分页大小，保存在对象中供后续校验、查询或展示
     * @param maxPayloadBytes 最大载荷字节，保存在对象中供后续校验、查询或展示
     * @param maxSelectionSize 最大选择大小，保存在对象中供后续校验、查询或展示
     */
    public record Limits(
            int maxPageSize,
            int maxPayloadBytes,
            int maxSelectionSize) {
    }

    /**
     * 封装结构的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param view 视图，保存在对象中供后续校验、查询或展示
     * @param entity 实体，保存在对象中供后续校验、查询或展示
     * @param list 列表，保存在对象中供后续校验、查询或展示
     * @param form 表单，保存在对象中供后续校验、查询或展示
     * @param actions 动作集合，保存在对象中供后续校验、查询或展示
     */
    public record Schema(
            SchemaView view,
            Entity entity,
            ListSchema list,
            Object form,
            List<ExternalActionDescriptor> actions) {
    }

    /**
     * 封装结构视图的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param surfaceType 界面类型标识，决定后续结构视图采用的处理分支
     */
    public record SchemaView(String key, String surfaceType) {
    }

    /**
     * 封装实体的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param name 展示名称，供界面或日志识别
     */
    public record Entity(String code, String name) {
    }

    /**
     * 封装列表结构的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param selection 选择，保存在对象中供后续校验、查询或展示
     * @param pagination {@code pagination}，保存在对象中供后续校验、查询或展示
     * @param columns 列集合，保存在对象中供后续校验、查询或展示
     * @param filters 过滤条件，保存在对象中供后续校验、查询或展示
     */
    public record ListSchema(
            Selection selection,
            Pagination pagination,
            List<Column> columns,
            List<Filter> filters) {
    }

    /**
     * 描述列表选择行为以及允许回传给宿主系统的值字段。
     *
     * <p>{@code returnableFields} 与展示列分离，iframe 可以渲染更多字段，但
     * {@code selection.changed} 只能回传发布快照明确授权的字段。</p>
     *
     * @param mode 模式标识，决定后续选择采用的处理分支
     * @param valueField 值字段，保存在对象中供后续校验、查询或展示
     * @param returnableFields {@code returnable}字段，保存在对象中供后续校验、查询或展示
     */
    public record Selection(
            String mode,
            String valueField,
            List<String> returnableFields) {
    }

    /**
     * 封装{@code pagination}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param allowTotal 允许总数，保存在对象中供后续校验、查询或展示
     * @param maxPageSize 最大分页大小，保存在对象中供后续校验、查询或展示
     */
    public record Pagination(boolean allowTotal, int maxPageSize) {
    }

    /**
     * 封装列的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param label 标签，后续用于处理列时匹配或展示
     * @param type 类型标识，决定后续列采用的处理分支
     * @param width {@code width}，保存在对象中供后续校验、查询或展示
     * @param sortable {@code sortable}，保存在对象中供后续校验、查询或展示
     * @param options 选项，保存在对象中供后续校验、查询或展示
     */
    public record Column(
            String code,
            String label,
            String type,
            Integer width,
            boolean sortable,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Option> options) {
    }

    /**
     * 封装过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param label 标签，后续用于处理过滤时匹配或展示
     * @param type 类型标识，决定后续过滤采用的处理分支
     * @param operator 操作人，保存在对象中供后续校验、查询或展示
     * @param options 选项，保存在对象中供后续校验、查询或展示
     */
    public record Filter(
            String code,
            String label,
            String type,
            String operator,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Option> options) {
    }

    /**
     * 封装选项的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param label 标签，后续用于处理选项时匹配或展示
     * @param value 待处理选项的原始输入，结果供调用方继续使用
     */
    public record Option(String label, Object value) {
    }

    /**
     * 封装外部动作描述的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param label 标签，后续用于处理外部动作描述时匹配或展示
     * @param placement {@code placement}，保存在对象中供后续校验、查询或展示
     * @param kind 类型，保存在对象中供后续校验、查询或展示
     * @param transport 传输，保存在对象中供后续校验、查询或展示
     * @param recordMode 记录模式标识，决定后续外部动作描述采用的处理分支
     * @param selectionMode 选择模式标识，决定后续外部动作描述采用的处理分支
     * @param dataSchema 数据结构，保存在对象中供后续校验、查询或展示
     * @param requiresRecordVersion 需要记录版本，保存在对象中供后续校验、查询或展示
     * @param inputSchema 输入结构，保存在对象中供后续校验、查询或展示
     * @param idempotencyRequired 幂等必填，保存在对象中供后续校验、查询或展示
     */
    public record ExternalActionDescriptor(
            String key,
            String label,
            String placement,
            String kind,
            String transport,
            String recordMode,
            String selectionMode,
            Object dataSchema,
            boolean requiresRecordVersion,
            Object inputSchema,
            boolean idempotencyRequired) {
    }

    /**
     * 封装列表的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param items 条目，保存在对象中供后续校验、查询或展示
     * @param hasMore {@code has}{@code more}，保存在对象中供后续校验、查询或展示
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param total 总数，保存在对象中供后续校验、查询或展示
     */
    public record ListResult(
            List<ListItem> items,
            boolean hasMore,
            int pageNum,
            int pageSize,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long total) {
    }

    /**
     * 封装列表条目的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param id 对象标识，供后续引用、更新或关联
     * @param recordVersion 记录版本，保存在对象中供后续校验、查询或展示
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param meta {@code meta}，保存在对象中供后续校验、查询或展示
     * @param actions 动作集合，保存在对象中供后续校验、查询或展示
     */
    public record ListItem(
            String id,
            Long recordVersion,
            Map<String, Object> values,
            ItemMeta meta,
            Map<String, ItemActionCapability> actions) {
    }

    /**
     * 封装条目{@code meta}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param updatedAt {@code updated}时间，后续用于判断有效期或展示该事件的发生时间
     */
    public record ItemMeta(Instant updatedAt) {
    }

    /**
     * 封装条目动作能力的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param visible 可见，保存在对象中供后续校验、查询或展示
     * @param enabled 启用，保存在对象中供后续校验、查询或展示
     * @param reason 原因，保存在对象中供后续校验、查询或展示
     */
    public record ItemActionCapability(
            boolean visible,
            boolean enabled,
            String reason) {
    }
}
