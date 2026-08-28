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
import com.workflow.embed.management.domain.EmbedManagementModel.GrantState;
import com.workflow.embed.management.domain.EmbedManagementModel.Page;
import com.workflow.embed.management.domain.EmbedManagementModel.PublishViewCommand;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.RevisionMode;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
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
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Embed View 草稿、发布快照与安全状态的应用服务。 */
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
        LocalDateTime now = now();
        String json = write(command.draft());
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_DRAFT_BYTES) {
            throw new EmbedManagementException(
                    413, "EMBED_DRAFT_TOO_LARGE", "draft 最大为 256 KiB");
        }
        int updated = repository.updateDraft(
                viewId, command.expectedVersion(), json, actor.userId(), now);
        if (updated != 1) {
            throw versionConflict(requireView(viewId));
        }
        ViewState result = requireView(viewId);
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.UPDATE,
                "更新 Embed View 草稿", "EMBED_VIEW", viewId, current.viewKey(),
                Map.of("draftRevision", current.draftRevision()),
                Map.of("draftRevision", result.draftRevision()), now);
        return result;
    }

    @Transactional(readOnly = true)
    public ValidationResult validate(String viewId, long expectedVersion) {
        ViewState view = requireMutableView(requireView(viewId));
        requireVersion(view, expectedVersion);
        return validator.validate(view.surfaceType(), read(view.draftConfigJson()));
    }

    /**
     * 在同一事务内重新校验并创建不可变 Release。
     *
     * <p>锁定 View 后再解析底层已发布资源，保证 revision 分配、快照写入和 active 指针更新原子。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseState publish(String viewId, PublishViewCommand command) {
        CurrentActor actor = EmbedManagementSupport.requireActor(actorProvider);
        ViewState view = requireMutableView(repository.lockView(viewId));
        requireVersion(view, command.expectedVersion());
        ValidationResult validation = validator.validate(
                view.surfaceType(), read(view.draftConfigJson()));
        if (!validation.valid()) {
            throw new EmbedManagementException(
                    422,
                    "EMBED_VIEW_VALIDATION_FAILED",
                    "Embed view validation failed",
                    Map.of("violations", validation.violations()));
        }
        LocalDateTime now = now();
        long revision = repository.nextReleaseRevision(viewId);
        JsonNode config = read(validation.canonicalConfig());
        ReleaseState release = new ReleaseState(
                "evr_" + IdWorker.getIdStr(),
                viewId,
                revision,
                view.surfaceType(),
                validation.resolved().entityCode(),
                validation.resolved().listKey(),
                validation.resolved().defaultFormId(),
                validation.resolved().listReleaseId(),
                validation.resolved().listReleaseVersion(),
                validation.resolved().formReleaseId(),
                validation.resolved().formReleaseVersion(),
                write(config.path("entryModes")),
                write(config.path("capabilities")),
                write(config.path("fieldPolicy")),
                write(config.path("actionPolicy")),
                write(config.path("contextSchema")),
                write(config.path("contextBindings")),
                write(config.path("ui")),
                validation.canonicalConfig(),
                validation.configHash(),
                trimToNull(command.releaseNote()),
                actor.userId(),
                now);
        requireFollowActiveCompatible(view, release, now);
        repository.insertRelease(release);
        if (repository.markPublished(viewId, command.expectedVersion(), release.id(),
                actor.userId(), now) != 1) {
            throw versionConflict(requireView(viewId));
        }
        EmbedManagementSupport.audit(auditPort, actor, AuditAction.PUBLISH,
                "发布 Embed View", "EMBED_VIEW_RELEASE", release.id(), view.viewKey(),
                null, Map.of("revision", revision, "configHash", release.configHash()), now);
        return release;
    }

    /**
     * 防止 FOLLOW_ACTIVE Grant 在 active 指针切换后失去已承诺的能力或入口。
     *
     * <p>同时检查尚未撤销的 DISABLED Grant：它们可能稍后恢复，若仅检查 ACTIVE 会把不兼容
     * 配置潜伏到启用时。Grant upsert 与 publish 共用 View 行锁，避免预检后的并发插入窗口。</p>
     */
    private void requireFollowActiveCompatible(
            ViewState view, ReleaseState candidate, LocalDateTime now) {
        List<GrantState> following = repository.findGrants(view.id()).stream()
                .filter(grant -> grant.revisionMode() == RevisionMode.FOLLOW_ACTIVE)
                .filter(grant -> grant.status() != SecurityStatus.REVOKED)
                .filter(grant -> grant.expiresAt() == null || grant.expiresAt().isAfter(now))
                .toList();
        if (following.isEmpty()) {
            return;
        }

        Set<String> candidateCapabilities = readStringSet(candidate.capabilitiesJson());
        List<String> incompatibleApplications = following.stream()
                .filter(grant -> !candidateCapabilities.containsAll(
                        readStringSet(grant.capabilityCeilingJson())))
                .map(GrantState::applicationId)
                .distinct()
                .toList();
        if (!incompatibleApplications.isEmpty()) {
            throw new EmbedManagementException(
                    422,
                    "EMBED_FOLLOW_ACTIVE_INCOMPATIBLE",
                    "新 Release 会移除 FOLLOW_ACTIVE Grant 已授权的能力",
                    Map.of("applicationIds", incompatibleApplications));
        }

        if (view.publishedRevision() != null) {
            ReleaseState current = repository.findRelease(view.id(), view.publishedRevision());
            if (current == null) {
                throw new IllegalStateException("当前 Embed View Release 数据损坏");
            }
            Set<String> candidateEntries = readStringSet(candidate.entryModesJson());
            Set<String> currentEntries = readStringSet(current.entryModesJson());
            if (!candidateEntries.containsAll(currentEntries)) {
                throw new EmbedManagementException(
                        422,
                        "EMBED_FOLLOW_ACTIVE_INCOMPATIBLE",
                        "新 Release 会移除 FOLLOW_ACTIVE Grant 正在使用的入口模式",
                        Map.of("removedEntryModes", currentEntries.stream()
                                .filter(entry -> !candidateEntries.contains(entry)).toList()));
            }
        }
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

    @Transactional(readOnly = true)
    public List<ReleaseState> releases(String viewId) {
        requireView(viewId);
        return repository.findReleases(viewId);
    }

    @Transactional(readOnly = true)
    public ReleaseState release(String viewId, long revision) {
        requireView(viewId);
        ReleaseState release = repository.findRelease(viewId, revision);
        if (release == null) {
            throw notFound("Embed View Release 不存在");
        }
        return release;
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
        // DRAFT 只允许通过 publish 原子进入 ACTIVE，或直接退役；允许先置为 DISABLED
        // 会制造一个状态图中不存在、且仍可继续发布的半初始化 View。
        if (current.status() == ViewStatus.DRAFT && target == ViewStatus.DISABLED) {
            throw conflict("EMBED_VIEW_STATUS_INVALID", "草稿 View 不能直接禁用");
        }
        if (target == ViewStatus.ACTIVE && current.publishedReleaseId() == null) {
            throw conflict("EMBED_VIEW_NOT_PUBLISHED", "尚未发布的 View 不能直接启用");
        }
    }

    private ViewState requireMutableView(ViewState view) {
        view = requireViewState(view);
        if (view.status() == ViewStatus.RETIRED) {
            throw conflict("EMBED_VIEW_RETIRED", "已退役 View 不能修改或发布");
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
        draft.set("releasePolicy", objectMapper.createObjectNode().put("strategy", "PINNED"));
        draft.putArray("capabilities");
        ObjectNode fields = draft.putObject("fieldPolicy");
        fields.putArray("visible");
        fields.putArray("queryable");
        fields.putArray("writable");
        fields.putArray("returnable");
        draft.putObject("actionPolicy").putArray("allowed");
        draft.putObject("contextSchema");
        draft.putArray("contextBindings");
        draft.putObject("ui");
        return write(draft);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed View JSON 数据损坏", exception);
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSON 文档无法序列化", exception);
        }
    }

    private Set<String> readStringSet(String json) {
        try {
            return new LinkedHashSet<>(List.of(objectMapper.readValue(json, String[].class)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embed Release 字符串数组 JSON 数据损坏", exception);
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
