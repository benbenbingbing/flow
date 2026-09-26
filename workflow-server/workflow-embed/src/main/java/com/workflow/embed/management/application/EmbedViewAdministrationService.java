package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.identity.model.CurrentActor;
import com.workflow.contracts.identity.port.CurrentActorPort;
import com.workflow.embed.management.api.error.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.application.port.EmbedManagementRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Embed View 当前配置与安全状态的应用服务。 */
@Service
public class EmbedViewAdministrationService {

    private static final Pattern VIEW_KEY = Pattern.compile("[a-z][a-z0-9._-]{0,99}");
    private static final int MAX_DRAFT_BYTES = 262_144;

    private final EmbedManagementRepository repository;
    private final EmbedViewConfigurationValidator validator;
    private final CurrentActorPort actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 初始化嵌入式视图管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储，保存在对象中供后续校验、查询或展示
     * @param validator 校验器，保存在对象中供后续校验、查询或展示
     * @param actorProvider 操作人提供者，保存在对象中供后续校验、查询或展示
     * @param auditPort 审计端口，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedViewAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(repository, validator, actorProvider, auditPort, objectMapper,
                Clock.systemUTC());
    }

    /**
     * 初始化嵌入式视图管理服务，保存构造参数供后续方法使用。
     *
     * @param repository 仓储依赖，保存到当前对象供后续业务方法调用
     * @param validator 校验器依赖，保存到当前对象供后续业务方法调用
     * @param actorProvider 操作人提供者依赖，保存到当前对象供后续业务方法调用
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    EmbedViewAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            CurrentActorPort actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.actorProvider = actorProvider;
        this.auditPort = auditPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 分页查询嵌入式视图管理；查询结果供调用方展示或继续处理。
     *
     * @param filter 过滤，作为 {@code repository.findViews} 的输入影响后续处理
     * @return 符合条件的{@code page<view}{@code state>}结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public Page<ViewState> page(ViewFilter filter) {
        return repository.findViews(filter);
    }

    /**
     * 读取视图状态；结果供调用方展示或继续处理。
     *
     * @param viewId 视图ID，后续用于读取嵌入式视图管理时定位或关联目标
     * @return 符合条件的视图状态结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public ViewState get(String viewId) {
        return requireView(viewId);
    }

    /**
     * 用与保存、Launch 相同的规则重新校验当前配置和最新 ACTIVE 资源。
     *
     * <p>该检查只读取当前状态，不生成 Release，也不修改 View 版本。</p>
     *
     * @param viewId 视图ID，后续用于校验当前活动时定位或关联目标
     * @return 校验后的当前活动结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public CurrentValidation validateCurrentActive(String viewId) {
        ViewState view = requireView(viewId);
        ValidationResult validation = validator.validateCurrentActive(
                view.surfaceType(), read(view.draftConfigJson()));
        return new CurrentValidation(view.status(), validation);
    }

    /**
     * 创建嵌入式视图管理；结果供后续流程传递或持久化。
     *
     * @param command 本次命令，后续经校验后用于创建嵌入式视图管理
     * @return 创建后的嵌入式视图管理结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public ViewState create(CreateViewCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        validateCreate(command);
        if (repository.findViewByKey(command.viewKey().trim()) != null) {
            throw conflict("EMBED_VIEW_KEY_CONFLICT", "viewKey 已存在");
        }
        LocalDateTime now = now();
        ViewState view = new ViewState(
                "ev_" + IdWorker.getIdStr(),
                command.viewKey().trim(),
                command.name().trim(),
                trimToNull(command.description()),
                command.surfaceType(),
                ViewStatus.DRAFT,
                initialDraft(command.surfaceType()),
                1L,
                null,
                null,
                1L,
                1L,
                actor.userId(),
                now,
                actor.userId(),
                now);
        try {
            repository.insertView(view);
        } catch (DuplicateKeyException exception) {
            // 预查只改善提示，真正并发唯一性仍由数据库裁决，并稳定映射为 409。
            throw conflict("EMBED_VIEW_KEY_CONFLICT", "viewKey 已存在");
        }
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.CREATE,
                "创建 Embed View", "EMBED_VIEW", view.id(), view.viewKey(),
                null, view, now);
        return view;
    }

    /**
     * 更新草稿；后续读取或执行将使用更新后的状态。
     *
     * @param viewId 视图ID，后续用于更新草稿时定位或关联目标
     * @param command 本次命令，后续经校验后用于更新草稿
     * @return 更新后的草稿结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public ViewState updateDraft(String viewId, UpdateDraftCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        ViewState current = requireMutableView(repository.lockView(viewId));
        requireVersion(current, command.expectedVersion());
        if (command.draft() == null || !command.draft().isObject()) {
            throw new IllegalArgumentException("draft 必须是 JSON Object");
        }
        ObjectNode normalized = validator.normalizedCurrentConfig(command.draft());
        String json = write(normalized);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DRAFT_BYTES) {
            // 体积门禁不依赖外部资源解析，保证超大请求稳定返回 413 且不做额外查询。
            throw new EmbedManagementException(
                    413, "EMBED_DRAFT_TOO_LARGE", "draft 最大为 256 KiB");
        }
        ValidationResult validation = validator.validateCurrentActive(
                current.surfaceType(), normalized);
        if (!validation.valid()) {
            // 保存即生效，因此必须在写库前完整解析当前 ACTIVE 资源；422 保证零落库。
            throw new EmbedManagementException(
                    422,
                    "EMBED_VIEW_VALIDATION_FAILED",
                    "Embed view validation failed",
                    Map.of("violations", validation.violations()));
        }
        LocalDateTime now = now();
        int updated = repository.updateDraft(
                viewId, command.expectedVersion(), json, actor.userId(), now);
        if (updated != 1) {
            throw versionConflict(requireView(viewId));
        }
        ViewState result = requireView(viewId);
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.UPDATE,
                "保存 Embed View 当前配置", "EMBED_VIEW", viewId, current.viewKey(),
                Map.of("status", current.status()),
                Map.of("status", result.status()), now);
        return result;
    }

    /**
     * 处理变更状态，并将结果传给后续步骤。
     *
     * @param viewId 视图ID，后续用于处理变更状态时定位或关联目标
     * @param command 本次命令，后续经校验后用于处理变更状态
     * @return 处理后的变更状态结果，供调用方继续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public StatusChangeResult changeStatus(String viewId, ChangeStatusCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        ViewState current = requireViewState(repository.lockView(viewId));
        requireVersion(current, command.expectedVersion());
        ViewStatus target = parseStatus(command.status());
        requireTransition(current, target);
        if (current.status() == target) {
            return new StatusChangeResult(current, 0L);
        }
        LocalDateTime now = now();
        if (repository.updateViewStatus(viewId, command.expectedVersion(), target.name(),
                actor.userId(), now) != 1) {
            throw versionConflict(requireView(viewId));
        }
        long affected = repository.countActiveSessionsByView(viewId);
        ViewState result = requireView(viewId);
        AuditAction action = target == ViewStatus.DISABLED
                ? AuditAction.DISABLE
                : target == ViewStatus.ACTIVE ? AuditAction.ENABLE : AuditAction.OTHER;
        EmbedManagementSupport.audit(auditPort, actor, action,
                "变更 Embed View 状态", "EMBED_VIEW", viewId, current.viewKey(),
                Map.of("status", current.status()),
                Map.of("status", target, "reason", nullToEmpty(command.reason())), now);
        return new StatusChangeResult(result, affected);
    }

    /**
     * 校验创建；不满足约束时阻止后续处理。
     *
     * @param command 本次命令，后续经校验后用于校验创建
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateCreate(CreateViewCommand command) {
        if (command == null || !StringUtils.hasText(command.viewKey())
                || !VIEW_KEY.matcher(command.viewKey().trim()).matches()) {
            throw new IllegalArgumentException(
                    "viewKey 必须以小写字母开头且只含小写字母、数字、点、下划线或连字符");
        }
        if (!StringUtils.hasText(command.name()) || command.name().trim().length() > 128) {
            throw new IllegalArgumentException("name 长度必须为 1 到 128");
        }
        if (command.description() != null && command.description().length() > 500) {
            throw new IllegalArgumentException("description 最大长度为 500");
        }
        if (command.surfaceType() == null) {
            throw new IllegalArgumentException("surfaceType 为必填");
        }
    }

    /**
     * 校验并获取{@code transition}；不满足约束时阻止后续处理。
     *
     * @param current 当前，供本方法校验并获取{@code transition}时使用
     * @param target 目标，供本方法校验并获取{@code transition}时使用
     */
    private void requireTransition(ViewState current, ViewStatus target) {
        if (current.status() == ViewStatus.RETIRED && target != ViewStatus.RETIRED) {
            throw conflict("EMBED_VIEW_RETIRED", "已退役 View 不能恢复");
        }
        if (target == ViewStatus.DRAFT) {
            throw conflict("EMBED_VIEW_STATUS_INVALID", "View 不能回退为 DRAFT");
        }
        // DRAFT 只能由 updateDraft 的有效保存原子转为 ACTIVE，状态 API 不得绕过校验。
        if (current.status() == ViewStatus.DRAFT
                && (target == ViewStatus.ACTIVE || target == ViewStatus.DISABLED)) {
            throw conflict("EMBED_VIEW_STATUS_INVALID", "草稿 View 必须先保存有效配置");
        }
    }

    /**
     * 校验并获取可变视图；不满足约束时阻止后续处理。
     *
     * @param view 视图，作为 {@code requireViewState} 的输入影响后续处理
     * @return 校验并获取后的可变视图结果，供调用方继续处理
     */
    private ViewState requireMutableView(ViewState view) {
        view = requireViewState(view);
        if (view.status() == ViewStatus.RETIRED) {
            throw conflict("EMBED_VIEW_RETIRED", "已退役 View 不能修改");
        }
        return view;
    }

    /**
     * 校验并获取视图；不满足约束时阻止后续处理。
     *
     * @param viewId 视图ID，后续用于校验并获取视图时定位或关联目标
     * @return 校验并获取后的视图结果，供调用方继续处理
     */
    private ViewState requireView(String viewId) {
        return requireViewState(repository.findView(viewId));
    }

    /**
     * 校验并获取视图状态；不满足约束时阻止后续处理。
     *
     * @param view 视图，供本方法校验并获取视图状态时使用
     * @return 校验并获取后的视图状态结果，供调用方继续处理
     */
    private static ViewState requireViewState(ViewState view) {
        if (view == null) {
            throw notFound("Embed View 不存在");
        }
        return view;
    }

    /**
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param view 视图，作为 {@code versionConflict} 的输入影响后续处理
     * @param expectedVersion 预期版本，供本方法校验并获取版本时使用
     */
    private static void requireVersion(ViewState view, long expectedVersion) {
        if (view.lockVersion() != expectedVersion) {
            throw versionConflict(view);
        }
    }

    /**
     * 解析状态；输出作为后续校验或处理的输入。
     *
     * @param value 待解析状态的原始输入，结果供调用方继续使用
     * @return 解析后的状态结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static ViewStatus parseStatus(String value) {
        try {
            return ViewStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status 仅允许 ACTIVE、DISABLED 或 RETIRED");
        }
    }

    /**
     * 生成初始草稿文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续初始草稿采用的处理分支
     * @return 处理后的初始草稿文本，供调用方比较或展示
     */
    private String initialDraft(com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType type) {
        ObjectNode draft = objectMapper.createObjectNode();
        draft.set("target", objectMapper.createObjectNode());
        draft.putArray("entryModes").add(type == com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType.LIST
                ? "LIST" : "VIEW");
        draft.putArray("capabilities");
        ObjectNode fields = draft.putObject("fieldPolicy");
        // LIST/FORM 都直接运行 Flow 原生发布页；新建配置从一开始就只保存
        // 跨域回传白名单，避免生成随后又要迁移的旧字段投影草稿。
        fields.put("mode", "FLOW_PUBLISHED");
        fields.putArray("returnable");
        draft.putObject("actionPolicy").putArray("allowed");
        draft.putObject("contextSchema");
        draft.putArray("contextBindings");
        draft.putObject("ui");
        return write(draft);
    }

    /**
     * 写入嵌入式视图管理；后续读取或执行将使用更新后的状态。
     *
     * @param node 节点，作为 {@code objectMapper.writeValueAsString} 的输入影响后续处理
     * @return 写入后的嵌入式视图管理文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 文档无法序列化", exception);
        }
    }

    /**
     * 读取嵌入式视图管理；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 读取后的嵌入式视图管理结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed 配置 JSON 数据损坏", exception);
        }
    }

    /**
     * 处理当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的当前时间结果，供调用方继续处理
     */
    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * 去除文本首尾空白，并将空白结果转为 null 供后续缺失值判断。
     *
     * @param value 待清理截止空值的原始输入，结果供调用方继续使用
     * @return 清理后的截止空值文本，供调用方比较或展示
     */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 生成空值截止空文本，供后续匹配或展示。
     *
     * @param value 待处理空值截止空的原始输入，结果供调用方继续使用
     * @return 处理后的空值截止空文本，供调用方比较或展示
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 构造版本冲突异常，供调用方区分失败原因。
     *
     * @param current 当前，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的版本冲突结果，供调用方继续处理
     */
    private static EmbedManagementException versionConflict(ViewState current) {
        return new EmbedManagementException(
                409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT",
                "Embed 配置版本冲突",
                Map.of("currentVersion", current.lockVersion()));
    }

    /**
     * 构造业务冲突异常，供调用方刷新或重试。
     *
     * @param code 编码，后续用于处理冲突时定位或关联目标
     * @param message 消息，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的冲突结果，供调用方继续处理
     */
    private static EmbedManagementException conflict(String code, String message) {
        return new EmbedManagementException(409, code, message);
    }

    /**
     * 构造目标不存在异常，供调用方终止后续处理。
     *
     * @param message 消息，作为 {@code EmbedManagementException} 的输入影响后续处理
     * @return 处理后的非已找到结果，供调用方继续处理
     */
    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_RESOURCE_NOT_FOUND", message);
    }

    /**
     * 封装状态变更的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param view 视图，保存在对象中供后续校验、查询或展示
     * @param affectedActiveSessions {@code affected}活动会话，保存在对象中供后续校验、查询或展示
     */
    public record StatusChangeResult(ViewState view, long affectedActiveSessions) {
    }

    /**
     * 同一只读事务中读取的 View 状态与当前 ACTIVE 资源校验结果。
     *
     * @param viewStatus 视图状态标识，决定后续当前校验采用的处理分支
     * @param validation 校验，保存在对象中供后续校验、查询或展示
     */
    public record CurrentValidation(ViewStatus viewStatus, ValidationResult validation) {
    }
}
