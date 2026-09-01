package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.embed.management.api.EmbedManagementException;
import com.workflow.embed.management.domain.EmbedManagementModel.ChangeStatusCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.CreateViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.UpdateDraftCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewFilter;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewState;
import com.workflow.embed.management.domain.EmbedManagementModel.ViewStatus;
import com.workflow.embed.management.port.EmbedManagementRepository;
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
    private final CurrentActorProvider actorProvider;
    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public EmbedViewAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            CurrentActorProvider actorProvider,
            SystemAuditPort auditPort,
            ObjectMapper objectMapper) {
        this(repository, validator, actorProvider, auditPort, objectMapper,
                Clock.systemUTC());
    }

    EmbedViewAdministrationService(
            EmbedManagementRepository repository,
            EmbedViewConfigurationValidator validator,
            CurrentActorProvider actorProvider,
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

    @Transactional(readOnly = true)
    public Page<ViewState> page(ViewFilter filter) {
        return repository.findViews(filter);
    }

    @Transactional(readOnly = true)
    public ViewState get(String viewId) {
        return requireView(viewId);
    }

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

    private ViewState requireMutableView(ViewState view) {
        view = requireViewState(view);
        if (view.status() == ViewStatus.RETIRED) {
            throw conflict("EMBED_VIEW_RETIRED", "已退役 View 不能修改");
        }
        return view;
    }

    private ViewState requireView(String viewId) {
        return requireViewState(repository.findView(viewId));
    }

    private static ViewState requireViewState(ViewState view) {
        if (view == null) {
            throw notFound("Embed View 不存在");
        }
        return view;
    }

    private static void requireVersion(ViewState view, long expectedVersion) {
        if (view.lockVersion() != expectedVersion) {
            throw versionConflict(view);
        }
    }

    private static ViewStatus parseStatus(String value) {
        try {
            return ViewStatus.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status 仅允许 ACTIVE、DISABLED 或 RETIRED");
        }
    }

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

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 文档无法序列化", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static EmbedManagementException versionConflict(ViewState current) {
        return new EmbedManagementException(
                409,
                "EMBED_CONFIGURATION_VERSION_CONFLICT",
                "Embed 配置版本冲突",
                Map.of("currentVersion", current.lockVersion()));
    }

    private static EmbedManagementException conflict(String code, String message) {
        return new EmbedManagementException(409, code, message);
    }

    private static EmbedManagementException notFound(String message) {
        return new EmbedManagementException(404, "EMBED_RESOURCE_NOT_FOUND", message);
    }

    public record StatusChangeResult(ViewState view, long affectedActiveSessions) {
    }
}
